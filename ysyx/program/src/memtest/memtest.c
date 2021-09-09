#include <am.h>
#include <klib.h>
#include <klib-macros.h>

#define ARR_SIZE 0x2000

int main(){
    putstr("START TEST...");
    uint16_t* data = (void*)(0x80200000);
    for(int i = 0; i < ARR_SIZE / sizeof(uint16_t); i++){
        data[i] = i;
    }
    putstr("ALL DATA PREPARED\n");
    for(int i = 0; i < ARR_SIZE / sizeof(uint16_t); i++){
        panic_on(data[i] != i, "");
    }
    putstr("ALL TESTS PASSED!!\n");
    return 0;
}