#!/bin/python

import os
# restore the id of ysyxSoCFull.v
# to ysyx_000000 before git push
os.chdir('../soc/')
os.system('sed -i s/ysyx_22040228/ysyx_000000/g ysyxSoCFull.v')
