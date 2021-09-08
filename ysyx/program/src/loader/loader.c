#include <am.h>
#include <klib.h>
#include <klib-macros.h>

extern uint32_t program_start;
extern uint32_t program_end;
extern uint32_t _pmem_start;
#define program_SIZE ((&program_end) - (&program_start))

void main(){
    uint32_t* program = (uint32_t*)&program_start;
    uint32_t* pmem    = (uint32_t*)&_pmem_start;
    while(program < &program_end){
        *pmem++ = *program++;
    }
    asm volatile("fence.i");
    int (*f)() = (int (*)())(0x80000000);
    f();
}
