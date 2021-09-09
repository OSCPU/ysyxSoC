#include <am.h>
#include <klib.h>
#include <klib-macros.h>

extern uint32_t program_start;
extern uint32_t program_end;
extern uint32_t _pmem_start;
#define program_SIZE ((&program_end) - (&program_start))

int main(){
    uint32_t* program = (uint32_t*)&program_start;
    uint32_t* pmem    = (uint32_t*)&_pmem_start;
    putstr("Loading program of size ");
    printf("%d: expect 128 \'#\'\n", (uint32_t)program_SIZE * sizeof(uint32_t));
    putstr("Loading.....");
    uint32_t step = (uint32_t)(&program_end - &program_start) / 128;
    uint32_t* pre = program;

    while(program < &program_end){
        *pmem++ = *program++;
        if((uint32_t)(program - pre) >= step){
            putch('#');
            pre = program;
        }
    }
    putstr("\nLoad Finished\n");
    asm volatile("fence.i");
    int (*f)() = (int (*)())(0x80000000);
    f();
    return 0;
}
