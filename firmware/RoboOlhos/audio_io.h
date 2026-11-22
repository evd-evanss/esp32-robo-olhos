#pragma once
#include "audio_config.h"

#if ROBO_AUDIO_ENABLED
#include <driver/i2s.h>
#include <math.h>

class RobotAudio {
 public:
  bool begin() {
    i2s_config_t config = {};
    config.mode = (i2s_mode_t)(I2S_MODE_MASTER | I2S_MODE_TX | I2S_MODE_RX);
    config.sample_rate = ROBO_AUDIO_SAMPLE_RATE;
    config.bits_per_sample = I2S_BITS_PER_SAMPLE_32BIT;
    config.channel_format = I2S_CHANNEL_FMT_RIGHT_LEFT;
    config.communication_format = I2S_COMM_FORMAT_STAND_I2S;
    config.intr_alloc_flags = ESP_INTR_FLAG_LEVEL1;
    config.dma_buf_count = 6;
    config.dma_buf_len = 256;
    config.use_apll = false;
    config.tx_desc_auto_clear = true;
    config.fixed_mclk = 0;
    i2s_pin_config_t pins = {};
    pins.mck_io_num = I2S_PIN_NO_CHANGE;
    pins.bck_io_num = ROBO_I2S_BCLK;
    pins.ws_io_num = ROBO_I2S_WS;
    pins.data_out_num = ROBO_I2S_SPEAKER_DATA;
    pins.data_in_num = ROBO_I2S_MIC_DATA;
    ready_ = i2s_driver_install(I2S_NUM_0, &config, 0, nullptr) == ESP_OK &&
             i2s_set_pin(I2S_NUM_0, &pins) == ESP_OK;
    if (ready_) i2s_zero_dma_buffer(I2S_NUM_0);
    return ready_;
  }

  void tick(Robot& robot, uint32_t now) {
    if (!ready_) return;
    int32_t raw[128];
    size_t bytes = 0;
    if (i2s_read(I2S_NUM_0, raw, sizeof(raw), &bytes, 0) != ESP_OK || bytes < 8) return;
    const size_t slots = bytes / sizeof(int32_t);
    uint64_t energy = 0;
    size_t samples = 0;
    for (size_t i = ROBO_MIC_RIGHT_CHANNEL ? 1 : 0; i < slots; i += 2) {
      int32_t sample = raw[i] >> 14;
      energy += (int64_t)sample * sample;
      ++samples;
    }
    if (!samples) return;
    level_ = (uint16_t)min(32767.0, sqrt((double)energy / samples));
    if (calibrationUntil_ == 0) calibrationUntil_ = now + 1500;
    if ((int32_t)(now - calibrationUntil_) < 0) {
      noiseFloor_ = noiseFloor_ ? (noiseFloor_ * 7 + level_) / 8 : level_;
      return;
    }
    uint16_t threshold = max((uint16_t)550, (uint16_t)(noiseFloor_ * 3));
    if (level_ > threshold) {
      voiceUntil_ = now + 450;
      robot.setInteraction(MODE_LISTENING, now, 500);
    } else if ((int32_t)(now - voiceUntil_) >= 0 && robot.interaction == MODE_LISTENING) {
      robot.setInteraction(MODE_IDLE, now);
    }
    noiseFloor_ = (noiseFloor_ * 127 + min(level_, threshold)) / 128;
  }

  void playTone(uint16_t frequency, uint16_t durationMs, uint8_t volume = 18) {
    if (!ready_ || !frequency || !durationMs) return;
    const size_t frames = 128;
    int32_t samples[frames * 2];
    uint32_t phase = 0;
    const uint32_t step = ((uint64_t)frequency << 32) / ROBO_AUDIO_SAMPLE_RATE;
    uint32_t remaining = (uint32_t)ROBO_AUDIO_SAMPLE_RATE * durationMs / 1000;
    while (remaining) {
      size_t count = min((uint32_t)frames, remaining);
      for (size_t i = 0; i < count; ++i) {
        int32_t wave = (phase & 0x80000000u) ? -(int32_t)volume : (int32_t)volume;
        int32_t sample = wave << 22;
        samples[i * 2] = sample;
        samples[i * 2 + 1] = sample;
        phase += step;
      }
      size_t written = 0;
      i2s_write(I2S_NUM_0, samples, count * 2 * sizeof(int32_t), &written, portMAX_DELAY);
      remaining -= count;
    }
    i2s_zero_dma_buffer(I2S_NUM_0);
  }

  bool ready() const { return ready_; }
  uint16_t level() const { return level_; }
  uint16_t noiseFloor() const { return noiseFloor_; }

 private:
  bool ready_ = false;
  uint16_t level_ = 0;
  uint16_t noiseFloor_ = 0;
  uint32_t calibrationUntil_ = 0;
  uint32_t voiceUntil_ = 0;
};

#else

class RobotAudio {
 public:
  bool begin() { return false; }
  void tick(Robot&, uint32_t) {}
  void playTone(uint16_t, uint16_t, uint8_t = 18) {}
  bool ready() const { return false; }
  uint16_t level() const { return 0; }
  uint16_t noiseFloor() const { return 0; }
};

#endif

