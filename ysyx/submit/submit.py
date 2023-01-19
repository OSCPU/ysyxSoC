#!/bin/python
import os
import sys

core_id = sys.argv[1:2][0]

file_list = [
    'rtthread-mem.png', 'cache_spec.md',
    '../lint/warning.md', 'ysyx_' + core_id + '.pdf',
    '../soc/' + 'ysyx_' + core_id + '.v'
]

all_exist = True
for v in file_list:
    if not os.path.exists(v):
        print(v + ' does not exist')
        all_exist = False
        break

if all_exist:
    repo_name = ''
    val = os.listdir('./')
    for v in val:
        if os.path.isdir(v):
            repo_name = v
            break

    for v in file_list:
        os.system('cp ' + v + ' ./' + repo_name)

    os.chdir('./' + repo_name)
    os.system('git add .')
    os.system('git commit -m "dc & vcs"')
    os.system('git push')
    os.chdir('../')
