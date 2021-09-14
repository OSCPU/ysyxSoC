#!/usr/bin/python
#coding:utf-8
import logging
logging.basicConfig(level=logging.WARNING,filename='./cpu-check.log',filemode='w',format='%(asctime)s - %(filename)s[line:%(lineno)d] - %(levelname)s: %(message)s')# use logging
stuNum = input("Please input your ID ( such as 888 ) :")
stuNum = int(stuNum)
print("Your file "+"ysyx_21"+str(stuNum).zfill(4)+".v"+" will be check\n")


##直接将从excel中读取的信号接口贴过来，以后就不需要重复读取excel
SigContent =[['input', 'clock'],
 ['input', 'reset'],
 ['input', 'io_interrupt'],
 ['input', 'io_master_awready'],
 ['output', 'io_master_awvalid'],
 ['output', '[31:0]', 'io_master_awaddr'],
 ['output', '[3:0]', 'io_master_awid'],
 ['output', '[7:0]', 'io_master_awlen'],
 ['output', '[2:0]', 'io_master_awsize'],
 ['output', '[1:0]', 'io_master_awburst'],
 ['input', 'io_master_wready'],
 ['output', 'io_master_wvalid'],
 ['output', '[63:0]', 'io_master_wdata'],
 ['output', '[7:0]', 'io_master_wstrb'],
 ['output', 'io_master_wlast'],
 ['output', 'io_master_bready'],
 ['input', 'io_master_bvalid'],
 ['input', '[1:0]', 'io_master_bresp'],
 ['input', '[3:0]', 'io_master_bid'],
 ['input', 'io_master_arready'],
 ['output', 'io_master_arvalid'],
 ['output', '[31:0]', 'io_master_araddr'],
 ['output', '[3:0]', 'io_master_arid'],
 ['output', '[7:0]', 'io_master_arlen'],
 ['output', '[2:0]', 'io_master_arsize'],
 ['output', '[1:0]', 'io_master_arburst'],
 ['output', 'io_master_rready'],
 ['input', 'io_master_rvalid'],
 ['input', '[1:0]', 'io_master_rresp'],
 ['input', '[63:0]', 'io_master_rdata'],
 ['input', 'io_master_rlast'],
 ['input', '[3:0]', 'io_master_rid'],
 ['output', 'io_slave_awready'],
 ['input', 'io_slave_awvalid'],
 ['input', '[31:0]', 'io_slave_awaddr'],
 ['input', '[3:0]', 'io_slave_awid'],
 ['input', '[7:0]', 'io_slave_awlen'],
 ['input', '[2:0]', 'io_slave_awsize'],
 ['input', '[1:0]', 'io_slave_awburst'],
 ['output', 'io_slave_wready'],
 ['input', 'io_slave_wvalid'],
 ['input', '[63:0]', 'io_slave_wdata'],
 ['input', '[7:0]', 'io_slave_wstrb'],
 ['input', 'io_slave_wlast'],
 ['input', 'io_slave_bready'],
 ['output', 'io_slave_bvalid'],
 ['output', '[1:0]', 'io_slave_bresp'],
 ['output', '[3:0]', 'io_slave_bid'],
 ['output', 'io_slave_arready'],
 ['input', 'io_slave_arvalid'],
 ['input', '[31:0]', 'io_slave_araddr'],
 ['input', '[3:0]', 'io_slave_arid'],
 ['input', '[7:0]', 'io_slave_arlen'],
 ['input', '[2:0]', 'io_slave_arsize'],
 ['input', '[1:0]', 'io_slave_arburst'],
 ['input', 'io_slave_rready'],
 ['output', 'io_slave_rvalid'],
 ['output', '[1:0]', 'io_slave_rresp'],
 ['output', '[63:0]', 'io_slave_rdata'],
 ['output', 'io_slave_rlast'],
 ['output', '[3:0]', 'io_slave_rid']]

##检测模块命名规范
Error= 0
countTopModule = 0
for line in open("ysyx_21"+str(stuNum).zfill(4)+".v","r"): #设置文件对象并读取每一行文
    if "module " in line and "endmodule" not in line:
        if "module ysyx_21"+str(stuNum).zfill(4)+"_" in line:
            pass
        elif "module ysyx_21"+str(stuNum).zfill(4) in line and "module ysyx_21"+str(stuNum).zfill(4)+"_" not in line:
#             print(line)
            countTopModule = countTopModule + 1
            pass#也许有学生所有模块都不加"_"连接，不规范，但是也能用，不过会影响之后的检查
        else:        
            logging.error('module name is not right:')
            logging.error(line+'\n')#更改为写入到log文件中
            Error = 1
                #print("no")# 更改为写入到log文件中，并注明错误原因
if countTopModule > 1 :
    Error = 1#模块命名不规范，多个模块命名
if Error == 1:
    print("Please check module name!!!\n")
    logging.error("Please check module name!!!")
#print(Error)

#检测信号接口规范
flag = 0
# Sig= []

Content = []
newContent = []
for line in open("ysyx_21"+str(stuNum).zfill(4)+".v","r"): #设置文件对象并读取每一行文
    if "module ysyx_21"+str(stuNum).zfill(4) in line and "module ysyx_21"+str(stuNum).zfill(4)+"_" not in line:
        flag = 1

    if flag == 1:
        Content.append(line)
#         print(Content)
#         print(line)
    if ")" in line and flag == 1:
        flag = 0
for j in Content[1:]:
    newContent.append(j.replace(',','').replace(')','').replace(';','').split())

for j in SigContent:
    if j in newContent:
#         print(j)
        pass
    else:
        print("Error, you need to check code in : "+ j[-1])
        logging.error("you need to check code in: "+j[-1])
        if Error < 2:
            Error = Error + 2
#print(Error)
logging.info('\n\n\n\n\n\n')
if Error==0:
    print("Your core is fine in module name and signal interface\n")
    logging.critical("Your core is fine in module name and signal interface\nThe core could be checked further!")
elif Error == 1:
    print("Your core has ERROR in module name\n")
    logging.error("Your core has ERROR in module name\n")
elif Error == 2:    
    print("Your core has ERROR in signal interface\n")
    logging.error("Your core has ERROR in signal interface\n")
elif Error == 3:
    print("Your core has ERROR in module name and signal interface\n")
    logging.error("Your core has ERROR in module name and signal interface\n")
