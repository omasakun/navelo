https://github.com/ZinggJM/GxEPD2/blob/66ea1cf2e2b739d71065d9c21384b7387b8187b4/src/epd/GxEPD2_154_D67.cpp#L297

```javascript
clearScreen() {
  _writeScreenBuffer(0x26)
  _writeScreenBuffer(0x24)
  refresh(false)
  initial_write = false
}

writeScreenBuffer() {
  if (initial_write) clearScreen()
  else _writeScreenBuffer(0x24)
}

writeScreenBufferAgain() {
  _writeScreenBuffer(0x24) // set current
  _writeScreenBuffer(0x26) // set previous
}

refresh(bool partial_update_mode) {
  if (partial_update_mode) {
    if (initial_refresh) {
      refresh(false)
    } else {
      _setPartialRamArea(x1, y1, w1, h1);
      _Update_Part(); // 0xfc, _power_is_on = true, wait
    }
  } else {
    _Update_Full(); // 0xf7, _power_is_on = false, wait
    initial_refresh = false;
  }
}

powerOff() {
  _powerOff()
}

hibernate() {
  _PowerOff();
  DEEP_SLEEP()
  _hibernating = true
  _init_display_done = false
}

_writeScreenBuffer(command) {
  if (!_init_display_done) _InitDisplay();
  _setPartialRamArea(0, 0, WIDTH, HEIGHT);
  _writeCommand(command);
  TRANSFER_DATA()
}

_PowerOn()
{
  if (!_power_is_on) {
    OX22(0xe0)
  }
  _power_is_on = true;
}

_PowerOff() {
  if (_power_is_on) {
    OX22(0x83)
  }
  _power_is_on = false;
  _using_partial_mode = false;
}

_InitDisplay() {
  if (_hibernating) _reset();
  INITIALIZE()
  _init_display_done = true;
}

_Update_Full() {
  OX22(0xf7)
  _power_is_on = false;
}

_Update_Part() {
  OX22(0xfc)
  _power_is_on = true;
}
```



```javascript
writeScreenBuffer() {
  if (initial_write) clearScreen()
  else _writeScreenBuffer(0x24)
}

clearScreen() {
  _writeScreenBuffer(0x24)
  refresh(false)
  initial_write = false
}


writeScreenBufferAgain() {
  _writeScreenBuffer(0x24)
}

_writeScreenBuffer(command) {
  if (!_init_display_done) _InitDisplay();
  _setPartialRamArea(0, 0, WIDTH, HEIGHT);
  _writeCommand(command);
  TRANSFER_DATA()
}

_InitDisplay() {
  if (_hibernating) _reset();
  INITIALIZE()
  _init_display_done = true;
}

refresh(bool partial_update_mode) {
  if (partial_update_mode && !initial_refresh) {
    _setPartialRamArea(x1, y1, w1, h1);
    OX22(0xfc)
    _power_is_on = true;
  } else {
    OX22(0xf7)
    _power_is_on = false;
    initial_refresh = false;
  }
}

powerOff() {
  if (_power_is_on) {
    OX22(0x83)
  }
  _power_is_on = false;
  _using_partial_mode = false;
}

hibernate() {
  powerOff();
  DEEP_SLEEP()
  _hibernating = true
  _init_display_done = false
}

```
