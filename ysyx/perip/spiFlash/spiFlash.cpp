#include <stdio.h>
#include <stdint.h>
#include <stdbool.h>
#include <assert.h>
#include <stdlib.h>
#include <cstring>
#include <svdpi.h>

#define Assert(cond, ...) \
  do { \
    if (!(cond)) { \
      fflush(stdout); \
      fprintf(stderr, "\33[1;31m"); \
      fprintf(stderr, __VA_ARGS__); \
      fprintf(stderr, "\33[0m\n"); \
      assert(cond); \
    } \
  } while (0)

#define FLASH_SIZE (256 * 1024 * 1024)

#define PAGE_SIZE 4096
#define PG_ALIGN __attribute((aligned(PAGE_SIZE)))

static inline bool in_flash(uint64_t addr) {
  return (addr < FLASH_SIZE);
}

static uint8_t flash[FLASH_SIZE] PG_ALIGN = {};

extern "C" void flash_read(uint64_t addr, uint64_t *data) {
  if (!data) return;
  Assert(in_flash(addr), "Flash address 0x%lx out of bound", addr);
  *data = *(uint64_t *)(flash + addr);
}

extern "C" void flash_init(char *img) {
  FILE *fp = fopen(img, "rb");
  Assert(fp, "Can not open '%s'", img);
  fseek(fp, 0, SEEK_END);
  uint64_t size = ftell(fp);
  fseek(fp, 0, SEEK_SET);
  assert(fread(flash, size, 1, fp) == 1);
  fclose(fp);
}

extern "C" void flash_memcpy(uint8_t* src, size_t len) {
  memcpy(flash, src, len);
}
