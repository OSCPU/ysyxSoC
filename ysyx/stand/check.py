#!/usr/bin/python

import sys
import re
import logging
from io import open

logging.basicConfig(
    level=logging.WARNING,
    filename='./check.log',
    filemode='w',
    format=
    '%(asctime)s-%(filename)s[line:%(lineno)d]-%(levelname)s: %(message)s')

# use logging
error = 0
top_mod_cnt = 0
core_id = sys.argv[1:2][0]
core_file = 'ysyx_' + core_id + '.v'
print('File ' + core_file + ' will be check\n')

sig_port = []


def parse(lin):
    v = lin.split('|')
    sig_def = v[2].lstrip(' `').rstrip(' `')
    sig_name = v[3].lstrip(' `').rstrip(' `')
    sig_port.append((sig_def, sig_name))
    # print(v)


with open('interface.md', 'r', encoding='utf-8') as f:
    for line in f:
        if 'input' in line or 'output' in line:
            parse(line)

# print(sig_port)

# module define check
with open('../soc/' + core_file, 'rt', encoding='utf-8') as f:
    for line in f:
        if 'module ysyx_' + core_id in line:
            if 'module ysyx_' + core_id + '_' in line:
                continue
            elif 'module ysyx_' + core_id in line:
                top_mod_cnt += 1
                # print(line)
                continue
            else:
                logging.error('module name is not right:')
                logging.error(line + '\n')
                error = 1
if top_mod_cnt > 1:
    error = 1
if error == 1:
    print('Please check module name!!!\n')
    logging.error('Please check module name!!!')
# print(error)

is_top = False
ori_cont = []
new_cont = []
fin_cont = []
# interface define check
with open('../soc/' + core_file, 'rt', encoding='utf-8') as f:
    for line in f:
        if 'module ysyx_' + core_id in line and 'module ysyx_' + core_id + '_' not in line:
            is_top = True

        if is_top is True:
            ori_cont.append(line)
        if ')' in line and is_top is True:
            is_top = False
    for j in ori_cont[1:]:
        new_cont.append(
            j.replace(',', '').replace(')', '').replace(';', '').split())

for i in new_cont:
    sig_dir = ''
    sig_bw = ''
    sig_name = ''
    for j in i:
        if j == 'input' or j == 'output':
            sig_dir = j
        elif j == 'wire' or ('//' in j) or ('/*' in j):
            print('error, you need to check code in : ' + i[-1])
            logging.error('you need to check code in: ' + i[-1])
            break
        elif '[' in j:
            if re.match('\[\d+:\d+\]', j) is None:
                print('error, you need to check code in : ' + i[-1])
                logging.error('you need to check code in: ' + i[-1])
                break
            else:
                sig_bw = j
        else:
            sig_name = j

    if sig_name != '':
        fin_cont.append((sig_dir + sig_bw, sig_name))

sig_port.sort()
fin_cont.sort()
if len(sig_port) != len(fin_cont):
    error = 2

if sig_port != fin_cont:
    error = 3

info_ok = 'Your core is FINE in module name and signal interface\n'
name_err = 'Your core has ERROR in module name\n'
io_num_err = 'signal interface num is different, stand: ' + str(
    len(sig_port)) + 'your core is: ' + str(len(fin_cont))
io_err = 'Your core has ERROR in signal interface\n'
logging.info('\n\n\n\n\n\n')
if error == 0:
    print(info_ok)
    logging.critical(info_ok)
elif error == 1:
    print(name_err)
    logging.error(name_err)
elif error == 2:
    print(io_num_err)
    logging.error(io_num_err)
elif error == 3:
    print(io_err)
    logging.error(io_err)
