use embedded_graphics::{
  pixelcolor::BinaryColor,
  prelude::*,
  primitives::{Circle, PrimitiveStyle},
};
use esp_idf_hal::{
  delay::Delay,
  spi::{self, SpiDriver, SpiDriverConfig},
};
use esp_idf_hal::{gpio::AnyInputPin, prelude::*};
use esp_idf_hal::{gpio::PinDriver, spi::SpiDeviceDriver};
use esp_idf_svc::log::EspLogger;
use esp_idf_svc::sys;

use display::{Weact154Display, HEIGHT, WIDTH};
use utils::spawn_heap_logger;

fn main() -> anyhow::Result<()> {
  // It is necessary to call this function once.
  // Otherwise some patches to the runtime implemented by esp-idf-sys might not link properly.
  // See https://github.com/esp-rs/esp-idf-template/issues/71
  sys::link_patches();
  EspLogger::initialize_default();

  println!("Hello, world!");
  spawn_heap_logger();

  // main_display()?;

  Ok(())
}

pub fn main_display() -> anyhow::Result<()> {
  let peripherals = Peripherals::take()?;
  let delay = Delay::new_default();

  let spi = peripherals.spi2;
  let sclk = peripherals.pins.gpio19;
  let sdo = peripherals.pins.gpio18;
  let sdi = Option::<AnyInputPin>::None;
  let cs = peripherals.pins.gpio16;

  let reset = PinDriver::output(peripherals.pins.gpio20)?;
  let busy = PinDriver::input(peripherals.pins.gpio21)?;
  let dc = PinDriver::output(peripherals.pins.gpio17)?;

  let spi_config = spi::config::Config::new().baudrate(20.MHz().into());
  let spi_driver = SpiDriver::new(spi, sclk, sdo, sdi, &SpiDriverConfig::new())?;
  let spi_device = SpiDeviceDriver::new(&spi_driver, Some(cs), &spi_config)?;

  let mut display = Weact154Display::new(spi_device, dc, reset, busy, delay);

  let mut x = 50;
  let mut y = 50;
  let mut dx = 6;
  let mut dy = 8;
  let radius: i32 = 20;

  display.clear(BinaryColor::On)?;
  display.refresh_full()?;

  loop {
    display.clear(BinaryColor::On)?;

    Circle::new(Point::new(x, y), radius as u32)
      .into_styled(PrimitiveStyle::with_fill(BinaryColor::Off))
      .draw(&mut display)?;

    display.refresh_partial_while_awake()?;
    // display.refresh_partial()?;

    x += dx;
    y += dy;

    if x <= 0 || x + radius >= WIDTH as i32 {
      dx = -dx;
    }
    if y <= 0 || y + radius >= HEIGHT as i32 {
      dy = -dy;
    }

    // sleep(Duration::from_millis(5000));
  }
}

/**
 * WeAct Studio Epaper Module 1.54 inch
 * https://github.com/WeActStudio/WeActStudio.EpaperModule
 * ZJY200200-0154DAAMFGN, SSD1681, 200x200, Black/White
 */
pub mod display {
  use embedded_graphics::{
    pixelcolor::BinaryColor,
    prelude::{DrawTarget, OriginDimensions, Size},
    Pixel,
  };
  use embedded_hal::{
    delay::DelayNs,
    digital::{InputPin, OutputPin},
    spi::SpiDevice,
  };
  use thiserror::Error;

  use self::command::*;

  #[derive(Error, Debug)]
  pub enum DisplayError {}

  pub const WIDTH: u8 = 200;
  pub const HEIGHT: u8 = 200;

  /** Epaper is sleeping most of the time */
  #[derive(Debug, Clone, Copy, PartialEq, Eq)]
  pub enum DisplayState {
    /** Normal operation */
    Active,
    /** No clock, no output load */
    Sleep,
    /** Can only be woken up by hardware reset */
    DeepSleep,
  }

  pub struct Weact154Display<SPI, DC, RST, BSY, DLY> {
    spi: SPI,
    dc: DC,
    reset: RST,
    busy: BSY,
    delay: DLY,
    pixels: [u8; WIDTH as usize * HEIGHT as usize / 8],
    state: DisplayState,
  }

  impl<SPI, DC, RST, BSY, DLY> Weact154Display<SPI, DC, RST, BSY, DLY>
  where
    SPI: SpiDevice,
    DC: OutputPin,
    RST: OutputPin,
    BSY: InputPin,
    DLY: DelayNs,
  {
    pub fn new(spi: SPI, dc: DC, reset: RST, busy: BSY, delay: DLY) -> Self {
      Self {
        spi,
        dc,
        reset,
        busy,
        delay,
        pixels: [0; WIDTH as usize * HEIGHT as usize / 8],
        state: DisplayState::DeepSleep,
      }
    }

    pub fn state(&self) -> DisplayState {
      self.state
    }

    pub fn sleep(&mut self) -> Result<(), DisplayError> {
      self.wait_until_idle()?;
      self.send_command(DISPLAY_UPDATE_CONTROL_2)?;
      self.send_data(&[0b1000_0011])?; // disable analog & clock signal
      self.send_command(MASTER_ACTIVATION)?;
      self.send_command(NOP)?;
      self.state = DisplayState::Sleep;
      Ok(())
    }
    pub fn deep_sleep(&mut self) -> Result<(), DisplayError> {
      self.sleep()?;

      self.wait_until_idle()?;
      self.send_command(DEEP_SLEEP_MODE)?;
      self.send_data(&[0x01])?; // deep sleep mode 1
      self.state = DisplayState::DeepSleep;
      Ok(())
    }

    pub fn refresh_full(&mut self) -> Result<(), DisplayError> {
      self.refresh(0b1111_0111)?; // display with mode 1, then sleep
      Ok(())
    }
    pub fn refresh_partial(&mut self) -> Result<(), DisplayError> {
      self.refresh(0b1111_1111)?; // display with mode 2, then sleep
      Ok(())
    }
    pub fn refresh_partial_while_awake(&mut self) -> Result<(), DisplayError> {
      self.refresh(0b1111_1100)?; // display with mode 2, without sleep
      self.state = DisplayState::Active;
      Ok(())
    }

    fn init(&mut self) -> Result<(), DisplayError> {
      if self.state == DisplayState::DeepSleep {
        self.reset.set_low().unwrap();
        self.delay(10);
        self.reset.set_high().unwrap();
        self.delay(10);

        self.send_command(SW_RESET)?;
        self.wait_until_idle()?;

        self.send_command(DRIVER_OUTPUT_CONTROL)?;
        self.send_data(&[HEIGHT - 1, 0x00, 0x00])?;

        self.send_command(DATA_ENTRY_MODE_SETTING)?;
        self.send_data(&[0x03])?; // X/Y increment

        // self.set_ram_area(0, 0, WIDTH, HEIGHT)?;

        self.send_command(BORDER_WAVEFORM_CONTROL)?;
        self.send_data(&[0x01])?; // white border (black: LSB=0)

        self.send_command(TEMPERATURE_SENSOR_SELECTION)?;
        self.send_data(&[0x80])?; // internal temperature sensor

        self.state = DisplayState::Sleep;
      }
      Ok(())
    }
    fn refresh(&mut self, mode: u8) -> Result<(), DisplayError> {
      self.init()?;
      self.wait_until_idle()?;

      self.set_ram_area(0, 0, WIDTH, HEIGHT)?;
      self.send_command(WRITE_RAM)?;
      self.send_pixels_data()?;

      self.send_command(DISPLAY_UPDATE_CONTROL_1)?;
      self.send_data(&[0x00])?; // display ram content
      self.send_command(DISPLAY_UPDATE_CONTROL_2)?;
      self.send_data(&[mode])?;
      self.send_command(MASTER_ACTIVATION)?;
      self.send_command(NOP)?;
      Ok(())
    }
    fn set_ram_area(&mut self, x: u8, y: u8, width: u8, height: u8) -> Result<(), DisplayError> {
      self.send_command(SET_RAM_X_ADDRESS_START_END_POSITION)?;
      self.send_data(&[x / 8, (x + width - 1) / 8])?;

      self.send_command(SET_RAM_Y_ADDRESS_START_END_POSITION)?;
      self.send_data(&[y, 0x00, y + height - 1, 0x00])?;

      self.send_command(SET_RAM_X_ADDRESS_POSITION)?;
      self.send_data(&[x / 8])?;

      self.send_command(SET_RAM_Y_ADDRESS_POSITION)?;
      self.send_data(&[y, 0x00])?;
      Ok(())
    }

    fn delay(&mut self, ms: u32) {
      DelayNs::delay_ms(&mut self.delay, ms);
    }

    fn send_command(&mut self, cmd: u8) -> Result<(), DisplayError> {
      self.dc.set_low().unwrap();
      self.spi.write(&[cmd]).unwrap();
      Ok(())
    }
    fn send_data(&mut self, data: &[u8]) -> Result<(), DisplayError> {
      self.dc.set_high().unwrap();
      self.spi.write(data).unwrap();
      Ok(())
    }
    fn send_pixels_data(&mut self) -> Result<(), DisplayError> {
      self.dc.set_high().unwrap();
      self.spi.write(&self.pixels).unwrap();
      Ok(())
    }

    pub fn is_busy(&mut self) -> Result<bool, DisplayError> {
      Ok(self.busy.is_high().unwrap())
    }
    pub fn wait_until_idle(&mut self) -> Result<(), DisplayError> {
      self.delay(1);
      while self.is_busy()? {
        // NOTE: make sure busy pin is correctly connected!
        // println!("busy");
        DelayNs::delay_ms(&mut self.delay, 10);
      }
      Ok(())
    }

    pub fn clear(&mut self, color: BinaryColor) -> Result<(), DisplayError> {
      self.pixels.fill(u8::from(color.is_on()) * 0xff);
      Ok(())
    }
  }

  impl<SPI, DC, RST, BSY, DLY> DrawTarget for Weact154Display<SPI, DC, RST, BSY, DLY>
  where
    SPI: SpiDevice,
    DC: OutputPin,
    RST: OutputPin,
    BSY: InputPin,
    DLY: DelayNs,
  {
    type Color = BinaryColor;
    type Error = DisplayError;

    fn draw_iter<I>(&mut self, pixels: I) -> Result<(), Self::Error>
    where
      I: IntoIterator<Item = Pixel<Self::Color>>,
    {
      for Pixel(coord, color) in pixels.into_iter() {
        if coord.x < 0 || coord.x >= WIDTH as i32 || coord.y < 0 || coord.y >= HEIGHT as i32 {
          continue;
        }
        let x = coord.x as usize;
        let y = coord.y as usize;
        let index = x + y * (WIDTH as usize);
        let byte_index = index / 8;
        let bit_index = index % 8;
        let mask = 0b10000000 >> bit_index;
        let color = u8::from(color.is_on()) << (7 - bit_index);
        self.pixels[byte_index] = (self.pixels[byte_index] & !mask) | color;
      }
      Ok(())
    }
  }

  impl<SPI, DC, RST, BSY, DLY> OriginDimensions for Weact154Display<SPI, DC, RST, BSY, DLY> {
    fn size(&self) -> Size {
      Size::new(WIDTH as u32, HEIGHT as u32)
    }
  }

  mod command {
    pub const DRIVER_OUTPUT_CONTROL: u8 = 0x01;
    pub const DEEP_SLEEP_MODE: u8 = 0x10;
    pub const DATA_ENTRY_MODE_SETTING: u8 = 0x11;
    pub const SW_RESET: u8 = 0x12;
    pub const TEMPERATURE_SENSOR_SELECTION: u8 = 0x18;
    pub const MASTER_ACTIVATION: u8 = 0x20;
    pub const DISPLAY_UPDATE_CONTROL_1: u8 = 0x21;
    pub const DISPLAY_UPDATE_CONTROL_2: u8 = 0x22;
    pub const WRITE_RAM: u8 = 0x24;
    pub const BORDER_WAVEFORM_CONTROL: u8 = 0x3c;
    pub const SET_RAM_X_ADDRESS_START_END_POSITION: u8 = 0x44;
    pub const SET_RAM_Y_ADDRESS_START_END_POSITION: u8 = 0x45;
    pub const SET_RAM_X_ADDRESS_POSITION: u8 = 0x4e;
    pub const SET_RAM_Y_ADDRESS_POSITION: u8 = 0x4f;
    pub const NOP: u8 = 0x7f;
  }
}

mod utils {
  use std::{
    thread::{sleep, spawn},
    time::Duration,
  };

  use esp_idf_svc::sys;
  use log::info;

  pub fn spawn_heap_logger() {
    spawn(move || loop {
      unsafe {
        info!(
          "free heap: {} (min: {})",
          sys::esp_get_free_heap_size(),
          sys::esp_get_minimum_free_heap_size()
        );
      }
      sleep(Duration::from_millis(5000));
    });
  }
}
