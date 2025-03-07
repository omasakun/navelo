use anyhow::Context;
use bitvec::prelude::*;
use ffmpeg_sidecar::command::FfmpegCommand;
use flate2::read::DeflateDecoder;
use image::{GrayImage, Luma};
use std::cmp;
use std::fs::{create_dir_all, remove_dir_all, remove_file, File};
use std::io::Read;
use std::iter::repeat;
use std::path::Path;

const WIDTH: u32 = 200;
const HEIGHT: u32 = 200;
const FRAME_SIZE: usize = (WIDTH * HEIGHT) as usize;

fn main() -> anyhow::Result<()> {
  let file = File::open("data/badapple.bits")?;
  let deflate_decoder = DeflateDecoder::new(file);

  let mut counts_iter = StreamingNumbersDecoder::new(deflate_decoder);

  let frames_dir = Path::new("data/frames");
  if !frames_dir.exists() {
    create_dir_all(frames_dir)?;
  }

  let mut frame_idx = 0;
  loop {
    let bits = match run_lengths_to_bitvec(&mut counts_iter, FRAME_SIZE) {
      Ok(bits) if bits.len() == FRAME_SIZE => bits,
      _ => break,
    };
    println!("Processing frame {}", frame_idx);

    let mut img = GrayImage::new(WIDTH, HEIGHT);
    for y in 0..HEIGHT {
      for x in 0..WIDTH {
        let bit_index = (y * WIDTH + x) as usize;
        let pixel_value = if bits[bit_index] { 255 } else { 0 };
        img.put_pixel(x, y, Luma([pixel_value]));
      }
    }
    let frame_path = frames_dir.join(format!("{:06}.png", frame_idx));
    img.save(frame_path)?;
    frame_idx += 1;
  }

  println!("Encoding video...");
  let _ = remove_file("data/reconstructed.mp4");
  FfmpegCommand::new()
    .input("data/frames/%06d.png")
    .args(["-r", "20", "-pix_fmt", "yuv420p", "-crf", "23", "-preset", "slow"])
    .output("data/reconstructed.mp4")
    .spawn()?
    .wait()?;

  remove_dir_all(frames_dir)?;

  println!("Done!");
  Ok(())
}

struct StreamingNumbersDecoder<R: Read> {
  inner: R,
}

impl<R: Read> StreamingNumbersDecoder<R> {
  fn new(reader: R) -> Self {
    Self { inner: reader }
  }
}

impl<R: Read> Iterator for StreamingNumbersDecoder<R> {
  type Item = anyhow::Result<u32>;

  fn next(&mut self) -> Option<Self::Item> {
    let mut marker = [0u8];
    if let Err(e) = self.inner.read_exact(&mut marker) {
      return if e.kind() == std::io::ErrorKind::UnexpectedEof {
        None
      } else {
        Some(Err(e.into()))
      };
    }
    let byte = marker[0];
    match byte {
      0..=250 => Some(Ok(byte as u32)),
      251 => {
        let mut buf = [0u8; 2];
        if let Err(e) = self.inner.read_exact(&mut buf) {
          return Some(Err(e.into()));
        }
        let count = u16::from_le_bytes(buf) as u32;
        Some(Ok(count))
      }
      252 => {
        let mut buf = [0u8; 4];
        if let Err(e) = self.inner.read_exact(&mut buf) {
          return Some(Err(e.into()));
        }
        let count = u32::from_le_bytes(buf);
        Some(Ok(count))
      }
      _ => Some(Err(anyhow::anyhow!("Invalid marker byte encountered: {}", byte))),
    }
  }
}

/// counts_iter から run-length の値を順次読み出し、指定の frame_size 分のビット列を生成します。
fn run_lengths_to_bitvec<I>(counts_iter: &mut I, frame_size: usize) -> anyhow::Result<BitVec<u8, Msb0>>
where
  I: Iterator<Item = anyhow::Result<u32>>,
{
  let mut bits = BitVec::<u8, Msb0>::with_capacity(frame_size);
  let mut current_bit = false;
  while bits.len() < frame_size {
    let count = counts_iter.next().context("Unexpected end of counts data")??;
    let available = frame_size - bits.len();
    let count = cmp::min(count, available as u32);
    bits.extend(repeat(current_bit).take(count as usize));
    current_bit = !current_bit;
  }
  Ok(bits)
}
