#!/bin/python

import os
import argparse

stud_id = '22040228'  # modify `stud_id` to your own value
app_type = ['flash', 'mem']
app = [('hello', 40, 'cmd'), ('memtest', 140, 'cmd'),
       ('rtthread', 1000, 'cmd'), ('muldiv', 60, 'cmd'), ('kdb', 1000, 'gui')]


def run_stand_check():
    os.chdir('stand')
    os.system('./check.py ' + stud_id)
    os.chdir('..')


def run_lint_check(tgt):
    os.system('make -C lint ID=' + stud_id + ' ' + tgt)


def modify_flash_mode(mode):
    if mode == 'fast':
        os.system(
            "sed -i 's/^\/\/\(`define FAST_FLASH\)/\\1/g' ./perip/spi/rtl/spi.v"
        )
    else:
        os.system(
            "sed -i 's/^\(`define FAST_FLASH\)/\/\/\\1/g' ./perip/spi/rtl/spi.v"
        )


def run_comp(mode):
    modify_flash_mode(mode)
    os.system('make -C sim ID=' + stud_id + ' build')


def run_test(val):
    print(val)
    for i in app_type:
        for j in app:
            if val[0] == i and val[1] == j[0]:
                cmd = 'make -C sim SOC_APP_TYPE=' + i + ' SOC_APP_NAME=' + j[
                    0] + ' SOC_SIM_TIME=' + str(j[1])
                if val[2] == 'gui' or val[2] == 'cmd':
                    if val[2] == j[2]:
                        cmd += ' SOC_SIM_MODE=' + val[2]
                    else:
                        print(j[0] + ' dont support ' + val[2] + ' mode')
                        return
                else:
                    print('error run mode, need to enter "cmd" or "gui"')
                    return

                if val[3] == 'no-wave' or val[3] == 'wave':
                    if val[3] == 'wave':
                        cmd += ' SOC_WAV_MODE=-d'
                else:
                    print('error wave mode, need to enter "no-wave" or "wave"')
                    return

                cmd += ' test'
                # print(cmd)
                os.system(cmd)


def run_reg_test():
    for i in app_type:
        for j in app:
            if j[2] == 'cmd':
                os.system('make -C sim SOC_APP_TYPE=' + i + ' SOC_APP_NAME=' +
                          j[0] + ' SOC_SIM_TIME=' + str(j[1]) +
                          ' SOC_SIM_MODE=cmd test')


def submit_code():
    os.chdir('submit')
    os.system('./submit.py ' + stud_id)
    os.chdir('..')


def run_soc_comp():
    os.system('make -C soc all')


def gen_test_prog():
    os.chdir('prog/src')
    for i in app_type:
        for j in app:
            print('i: ' + i + ' j: ' + j[0])
            os.system("sed -i \"s/\(^APP_TYPE\s\+=\s\+\)'[a-z]\+'/\\1'" + i +
                      "'/\" run.py")
            os.system("sed -i \"s/\(^APP_NAME\s\+=\s\+\)'[a-z]\+'/\\1'" +
                      j[0] + "'/\" run.py")
            os.system('./run.py')


parser = argparse.ArgumentParser(description='OSCPU Season 4 SoC Test')
parser.add_argument('-s',
                    '--stand',
                    help='run interface standard check',
                    action='store_true')
parser.add_argument('-l',
                    '--lint',
                    help='run code lint check',
                    action='store_true')
parser.add_argument('-lu',
                    '--lint_unused',
                    help='run code lint with unused check',
                    action='store_true')

parser.add_argument('-c',
                    '--comp',
                    help='compile core with SoC in normal flash mode',
                    action='store_true')

parser.add_argument('-fc',
                    '--fst_comp',
                    help='compile core with SoC in fast flash mode',
                    action='store_true')

parser.add_argument(
    '-t',
    '--test',
    help=
    'Example: ./main.py -t [flash|mem] [hello|memtest|rtthread|muldiv|kdb] ' +
    '[cmd|gui] [no-wave|wave]. note: some programs dont support gui mode,' +
    ' so need to set right mode carefully',
    nargs=4)

parser.add_argument('-r',
                    '--regress',
                    help='run all test in normal flash mode',
                    action='store_true')

parser.add_argument('-fr',
                    '--fst_regress',
                    help='run all test in fast flash mode',
                    action='store_true')

parser.add_argument('-su',
                    '--submit',
                    help='submit code and spec to CICD',
                    action='store_true')

parser.add_argument('-y',
                    '--ysyx',
                    help='compile ysyxSoCFull framework[NOT REQUIRED]',
                    action='store_true')

parser.add_argument('-p',
                    '--prog',
                    help='compile all test prog[NOT REQUIRED]',
                    action='store_true')

args = parser.parse_args()
if args.stand:
    run_stand_check()
elif args.lint or args.lint_unused:
    run_lint_check('lint' if args.lint else 'lint-unused')
elif args.comp or args.fst_comp:
    run_comp('normal' if args.comp else 'fast')
elif args.regress or args.fst_regress:
    run_comp('normal' if args.regress else 'fast')
    run_reg_test()
elif args.submit:
    submit_code()
elif args.ysyx:
    run_soc_comp()
elif args.prog:
    gen_test_prog()
else:
    run_test(args.test)
