#pragma once

// Hardware wired according to the project schematic.
#define ROBO_AUDIO_ENABLED 1

// Shared I2S clocks. These pins are free on the ESP32-S3 N16R8 board used here.
#define ROBO_I2S_BCLK 16
#define ROBO_I2S_WS 17
#define ROBO_I2S_MIC_DATA 18
#define ROBO_I2S_SPEAKER_DATA 21

#define ROBO_AUDIO_SAMPLE_RATE 16000
#define ROBO_MIC_RIGHT_CHANNEL 0

