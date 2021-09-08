.section .rodata
.align 3
.global program_start, program_end
program_start:
#ifdef BIN_PATH
.incbin BIN_PATH
#endif
program_end:
