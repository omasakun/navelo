use bitvec::prelude::*;
use ffmpeg_sidecar::command::FfmpegCommand;
use ffmpeg_sidecar::event::OutputVideoFrame;
use flate2::write::DeflateEncoder;
use flate2::Compression;
use std::fs::File;
use std::io::Write;

fn main() -> anyhow::Result<()> {
  let iter = FfmpegCommand::new()
    .input("data/badapple.mp4")
    .args([
      "-r",
      "20",
      "-vf",
      "scale=200:200:force_original_aspect_ratio=1,pad=200:200:(ow-iw)/2:(oh-ih)/2:black",
    ])
    .rawvideo()
    .spawn()?
    .iter()?;

  let mut data = Vec::new();

  for (i, frame) in iter.filter_frames().enumerate() {
    println!("Processing frame {}", i);

    let bits = bitvec_from_frame(&frame);
    let counts = count_run_lengths(&bits);
    let bytes = encode_numbers(&counts);
    data.extend_from_slice(&bytes);
  }

  let data = compress_deflate(&data)?;

  let mut outfile = File::create("data/badapple.bits")?;
  outfile.write_all(&data)?;

  println!("Done!");
  Ok(())
}

fn bitvec_from_frame(frame: &OutputVideoFrame) -> BitVec<u8, Msb0> {
  let mut bits = BitVec::<u8, Msb0>::new();
  for chunk in frame.data.chunks(3) {
    let bit = is_color_bright(chunk[0], chunk[1], chunk[2]);
    bits.push(bit);
  }
  bits
}

// better compression
#[allow(dead_code)]
fn bitvec_from_frame_v2(frame: &OutputVideoFrame) -> BitVec<u8, Msb0> {
  let mut bits = BitVec::<u8, Msb0>::new();
  let width = frame.width as usize;
  let height = frame.height as usize;
  let data = &frame.data;

  for x in (0..width).step_by(8) {
    for y in 0..height {
      for dx in 0..8 {
        let index = (y * width + (x + dx)) * 3;
        let bit = is_color_bright(data[index], data[index + 1], data[index + 2]);
        bits.push(bit);
      }
    }
  }
  bits
}

fn is_color_bright(red: u8, green: u8, blue: u8) -> bool {
  let gray_value = (0.299 * red as f32 + 0.587 * green as f32 + 0.114 * blue as f32) as u8;
  gray_value > 128
}

fn count_run_lengths<T: BitStore, O: BitOrder>(bits: &BitVec<T, O>) -> Vec<u32> {
  let mut counts = Vec::new();
  let mut current_bit = false;
  let mut count = 0;
  for bit in bits {
    if *bit == current_bit {
      count += 1;
    } else {
      counts.push(count);
      current_bit = *bit;
      count = 1;
    }
  }
  counts.push(count);
  counts
}

fn encode_numbers(counts: &[u32]) -> Vec<u8> {
  let mut bytes = Vec::new();
  for &count in counts {
    if count <= 250 {
      bytes.push(count as u8);
    } else if count < 65536 {
      bytes.push(251);
      bytes.extend_from_slice(&(count as u16).to_le_bytes());
    } else {
      bytes.push(252);
      bytes.extend_from_slice(&count.to_le_bytes());
    }
  }
  bytes
}

fn compress_deflate(data: &[u8]) -> anyhow::Result<Vec<u8>> {
  let mut encoder = DeflateEncoder::new(Vec::new(), Compression::default());
  encoder.write_all(data)?;
  let compressed_data = encoder.finish()?;
  Ok(compressed_data)
}
