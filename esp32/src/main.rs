use embedded_graphics::{
  pixelcolor::BinaryColor,
  prelude::*,
  primitives::{Circle, PrimitiveStyle},
};
use esp_idf_hal::{
  delay::Delay,
  i2c::{I2cConfig, I2cDriver},
  spi::{self, SpiDriver, SpiDriverConfig},
};
use esp_idf_hal::{gpio::AnyInputPin, prelude::*};
use esp_idf_hal::{gpio::PinDriver, spi::SpiDeviceDriver};
use esp_idf_svc::log::EspLogger;
use esp_idf_svc::sys;

use display::{Weact154Display, HEIGHT, WIDTH};
use sensors::Gy87;
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
  main_gy87()?;

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

    // delay.delay_ms(5000);
  }
}

/**
 * GY87 IMU Module (MPU6050 + HMC5883L + BMP180)
 * MPU6050 Spec: https://www.alldatasheet.com/datasheet-pdf/view/517746/ETC1/MPU6050.html
 * MPU6050 Register Map: https://www.alldatasheet.com/datasheet-pdf/view/1132809/TDK/MPU6050.html
 * HMC5883L Datasheet: https://www.alldatasheet.com/datasheet-pdf/view/428790/HONEYWELL/HMC5883L.html
 * BMP180 Datasheet: https://www.alldatasheet.com/datasheet-pdf/view/1132068/BOSCH/BMP180.html
 */
pub fn main_gy87() -> anyhow::Result<()> {
  let peripherals = Peripherals::take()?;
  let delay = Delay::new_default();

  let i2c = peripherals.i2c0;
  let sda = peripherals.pins.gpio22;
  let scl = peripherals.pins.gpio23;

  let i2c_config = I2cConfig::new().baudrate(400.kHz().into());
  let i2c_driver = I2cDriver::new(i2c, sda, scl, &i2c_config)?;

  let mut gy87 = Gy87::new(i2c_driver, delay);

  gy87.init()?;
  println!("initialized");

  loop {
    let mpu = gy87.read_mpu()?;
    let hmc = gy87.read_hmc()?;
    let bmp = gy87.read_bmp()?;

    println!(
      "acc: ({}, {}, {}), temp: {}, gyro: ({}, {}, {})",
      mpu.acc_x, mpu.acc_y, mpu.acc_z, mpu.temp, mpu.gyro_x, mpu.gyro_y, mpu.gyro_z
    );
    println!("magnetometer: ({} Ga, {} Ga, {} Ga)", hmc.x, hmc.y, hmc.z);
    println!(
      "temperature: {} C, pressure: {} hPa",
      bmp.temperature,
      bmp.pressure / 100.0
    );

    delay.delay_ms(1000);
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

/**
 * GY87 IMU Module (MPU6050 + HMC5883L + BMP180)
 * MPU6050 Spec: https://www.alldatasheet.com/datasheet-pdf/view/517746/ETC1/MPU6050.html
 * MPU6050 Register Map: https://www.alldatasheet.com/datasheet-pdf/view/1132809/TDK/MPU6050.html
 * HMC5883L Datasheet: https://www.alldatasheet.com/datasheet-pdf/view/428790/HONEYWELL/HMC5883L.html
 * BMP180 Datasheet: https://www.alldatasheet.com/datasheet-pdf/view/1132068/BOSCH/BMP180.html
 */
pub mod sensors {
  use esp_idf_hal::{
    delay::{Delay, BLOCK},
    i2c::I2cDriver,
  };

  #[derive(Debug, Clone, Copy)]
  struct Bmp180Calibration {
    ac1: i64,
    ac2: i64,
    ac3: i64,
    ac4: i64,
    ac5: i64,
    ac6: i64,
    b1: i64,
    b2: i64,
    mc: i64,
    md: i64,
  }

  #[derive(Debug, Clone, Copy)]
  pub struct MpuValues {
    pub acc_x: f32,
    pub acc_y: f32,
    pub acc_z: f32,
    pub temp: f32,
    pub gyro_x: f32,
    pub gyro_y: f32,
    pub gyro_z: f32,
  }

  #[derive(Debug, Clone, Copy)]
  pub struct HmcValues {
    pub x: f32,
    pub y: f32,
    pub z: f32,
  }

  #[derive(Debug, Clone, Copy)]
  pub struct BmpValues {
    pub temperature: f32,
    pub pressure: f32,
  }

  pub struct Gy87<'a> {
    i2c: I2cDriver<'a>,
    delay: Delay,
    mpu_addr: u8,
    hmc_addr: u8,
    bmp_addr: u8,
    initialized: bool,
    hmc_gain: Option<f32>,
    bmp_calib: Option<Bmp180Calibration>,
  }

  impl<'a> Gy87<'a> {
    pub fn new(i2c: I2cDriver<'a>, delay: Delay) -> Self {
      Self {
        i2c,
        delay,
        mpu_addr: 0x68,
        hmc_addr: 0x1e,
        bmp_addr: 0x77,
        initialized: false,
        hmc_gain: None,
        bmp_calib: None,
      }
    }

    pub fn init(&mut self) -> anyhow::Result<()> {
      if self.initialized {
        return Ok(());
      }

      // === MPU6050 === //
      self.write(self.mpu_addr, &[0x6b, 0b0000_0001])?; // wake up and set clock source
      self.write(self.mpu_addr, &[0x1a, 0b0000_0101])?; // 10 Hz low pass filter
      self.write(self.mpu_addr, &[0x37, 0b0000_0010])?; // enable i2c bypass

      // === HMC5883L === //
      self.write(self.hmc_addr, &[0x00, 0b1111_0000])?; // 8 samples average, 15 Hz
      self.write(self.hmc_addr, &[0x01, 0b0010_0000])?; // range 1.3 Ga
      self.write(self.hmc_addr, &[0x02, 0b0000_0000])?; // continuous mode
      self.hmc_gain = Some(1.0 / 1090.0); // for 1.3 Ga

      // === BMP180 === //
      self.write(self.bmp_addr, &[0xaa])?;
      let calib = self.read::<[u8; 22]>(self.bmp_addr, 0xaa)?;
      self.bmp_calib = Some(Bmp180Calibration {
        ac1: i16::from_be_bytes([calib[0], calib[1]]) as i64,
        ac2: i16::from_be_bytes([calib[2], calib[3]]) as i64,
        ac3: i16::from_be_bytes([calib[4], calib[5]]) as i64,
        ac4: u16::from_be_bytes([calib[6], calib[7]]) as i64,
        ac5: u16::from_be_bytes([calib[8], calib[9]]) as i64,
        ac6: u16::from_be_bytes([calib[10], calib[11]]) as i64,
        b1: i16::from_be_bytes([calib[12], calib[13]]) as i64,
        b2: i16::from_be_bytes([calib[14], calib[15]]) as i64,
        mc: i16::from_be_bytes([calib[18], calib[19]]) as i64,
        md: i16::from_be_bytes([calib[20], calib[21]]) as i64,
      });

      self.initialized = true;
      Ok(())
    }

    // TODO: self test

    pub fn read_mpu(&mut self) -> anyhow::Result<MpuValues> {
      self.init()?;

      let reg = self.read::<[u8; 14]>(self.mpu_addr, 0x3b)?;

      Ok(MpuValues {
        acc_x: i16::from_be_bytes([reg[0], reg[1]]) as f32 / 16384.0,
        acc_y: i16::from_be_bytes([reg[2], reg[3]]) as f32 / 16384.0,
        acc_z: i16::from_be_bytes([reg[4], reg[5]]) as f32 / 16384.0,
        temp: i16::from_be_bytes([reg[6], reg[7]]) as f32 / 340.0 + 36.53,
        gyro_x: i16::from_be_bytes([reg[8], reg[9]]) as f32 / 131.0,
        gyro_y: i16::from_be_bytes([reg[10], reg[11]]) as f32 / 131.0,
        gyro_z: i16::from_be_bytes([reg[12], reg[13]]) as f32 / 131.0,
      })
    }
    pub fn read_hmc(&mut self) -> anyhow::Result<HmcValues> {
      self.init()?;

      let reg = self.read::<[u8; 6]>(self.hmc_addr, 0x03)?;

      Ok(HmcValues {
        x: i16::from_be_bytes([reg[0], reg[1]]) as f32 * self.hmc_gain.unwrap(),
        y: i16::from_be_bytes([reg[2], reg[3]]) as f32 * self.hmc_gain.unwrap(),
        z: i16::from_be_bytes([reg[4], reg[5]]) as f32 * self.hmc_gain.unwrap(),
      })
    }
    pub fn read_bmp(&mut self) -> anyhow::Result<BmpValues> {
      let oss = 3; // 8 samples, 26 ms
      let sleep = [5, 8, 14, 26][oss as usize];

      self.init()?;

      self.write(self.bmp_addr, &[0xf4, 0x2e])?;
      self.delay.delay_ms(5); // at least 4.5 ms
      let ut = self.read::<[u8; 2]>(self.bmp_addr, 0xf6)?;
      let ut = u16::from_be_bytes([ut[0], ut[1]]) as i64;

      self.write(self.bmp_addr, &[0xf4, 0x34 + (oss << 6)])?;
      self.delay.delay_ms(sleep);
      let up = self.read::<[u8; 3]>(self.bmp_addr, 0xf6)?;
      let up = u32::from_be_bytes([0, up[0], up[1], up[2]]) as i64;
      let up = up >> (8 - oss);

      let Bmp180Calibration {
        ac1,
        ac2,
        ac3,
        ac4,
        ac5,
        ac6,
        b1,
        b2,
        mc,
        md,
      } = self.bmp_calib.unwrap();

      let x1 = ((ut - ac6) * ac5) >> 15;
      let x2 = (mc << 11) / (x1 + md);
      let b5 = x1 + x2;
      let temperature = (b5 + 8) as f32 / 160.0;

      let b6 = b5 - 4000;
      let x1 = (b2 * ((b6 * b6) >> 12)) >> 11;
      let x2 = (ac2 * b6) >> 11;
      let x3 = x1 + x2;
      let b3 = (((ac1 * 4 + x3) << oss) + 2) / 4;
      let x1 = (ac3 * b6) >> 13;
      let x2 = (b1 * ((b6 * b6) >> 12)) >> 16;
      let x3 = ((x1 + x2) + 2) >> 2;
      let b4 = (ac4 * (x3 + 32768)) >> 15;
      let b7 = (up - b3) * (50000 >> oss);
      let p = b7 * 2 / b4;
      let x1 = (p >> 8) * (p >> 8);
      let x1 = (x1 * 3038) >> 16;
      let x2 = (-7357 * p) >> 16;
      let pressure = p + ((x1 + x2 + 3791) >> 4);
      let pressure = pressure as f32;

      Ok(BmpValues { temperature, pressure })
    }

    fn read<T: Default + AsMut<[u8]>>(&mut self, addr: u8, reg: u8) -> anyhow::Result<T> {
      let mut data = T::default();
      self.i2c.write(addr, &[reg], BLOCK)?;
      self.i2c.read(addr, data.as_mut(), BLOCK)?;
      Ok(data)
    }
    fn write(&mut self, addr: u8, data: &[u8]) -> anyhow::Result<()> {
      self.i2c.write(addr, data, BLOCK)?;
      Ok(())
    }
  }
}

mod utils {
  use std::{
    thread::{sleep, spawn},
    time::Duration,
  };

  use esp_idf_hal::{delay::BLOCK, i2c::I2cDriver};
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

  pub fn scan_i2c(i2c: &mut I2cDriver) {
    for addr in 0x00..=0x7f {
      if i2c.write(addr, &[], BLOCK).is_ok() {
        println!("found device at 0x{:02x}", addr);
      }
    }
  }
}
