#! /usr/bin/python3

""" Find stack usage using
 - compiler generated tables of stack use per function, *.c.su
 - run time trace output from compiler added enter/exit calls.

Just for ofdm_stack at this point

This script expects to be run in the .../build_linux/unittest directory!
"""

COMP_DIR   = 'CMakeFiles/ofdm_stack.dir'
EXE_FILE   = './ofdm_stack'

import sys
import os
import argparse
import pathlib
import subprocess

##########################
# Options

## Trace file (name) or default
## Use existing trace file or run command
argparser = argparse.ArgumentParser()
argparser.add_argument('-f', '--trace_file', action='store', default='function_trace.out',
                    help='Name of trace file, (default is "function_trace.out"')
argparser.add_argument('-x', '--exec', action='store_true', default=False,
                    help='Execute program')

args = argparser.parse_args()


##########################
# Checking
cwd_path = pathlib.Path.cwd()
# One simple thing we can handle is running from above unittest (in build_linux)
if ((cwd_path.name != 'unittest') and
    (pathlib.Path('unittest').exists())):
    os.chdir('unittest')

# Required files
assert(pathlib.Path(COMP_DIR).exists())
assert(pathlib.Path(COMP_DIR + '/ofdm_stack.c.su').exists())
assert(pathlib.Path(COMP_DIR + '/__/src/ofdm.c.su').exists())


##########################
# If trace file not found, or option set, run command
if ( not (pathlib.Path(args.trace_file).exists())):
    print('Trace file "{}" not found, running program'.format(args.trace_file))
    args.exec = True

if (args.exec):
    print('Running program: "{}"'.format(EXE_FILE))
    assert(pathlib.Path(EXE_FILE))
    result = subprocess.run([EXE_FILE],
                             stdout=subprocess.PIPE,
                             stderr=subprocess.STDOUT)
    if (result.returncode != 0):
        print('Error: traced program failed! Output:\n{}'.format(result.stdout))
assert(pathlib.Path(args.trace_file).exists())


##########################
# Data Structures
su_data = {} # <function> : <stack_size>
funcs_used = {} # <function> : count

##########################
# Read compiler generated tables of stack use per function, *.c.su
#
#  ofdm_stack.c:184:6:dummy_code	16	static
#

p = pathlib.Path(COMP_DIR)
for fpath in p.rglob('*.c.su'):
    with fpath.open() as f:
        for line in f.readlines():
            try:
                words = line.split()
                size = int(words[1])
                words = words[0].split(':')
                su_data[words[3]] = size
            except: pass # skip this line if there are errors

##########################
# Read trace file, convert addresses to names, track stack

max_stack_depth = 0
cur_stack_depth = 0
stack = []  # List of tuples of (function names, cur_stack_depth)
last_func = 'start'

def walk_stack():
    trace = ''
    for entry in stack:
        trace += entry[0] + ' '
    return(trace)

# Open trace
with open(args.trace_file, "r") as tf:
    for line in tf.readlines():
        #print('Line: "{}"'.format(line.strip()))
        words = line.split()
        # Note addr2line needs addr in hex!
        addr = words[1]
        if (words[0] == 'e'):
            # Note: This could be run once with a pipe if needed for faster operation.
            result = subprocess.run(['addr2line', '-f', addr, '-e', EXE_FILE],
                                stdout=subprocess.PIPE)
            result.check_returncode()
            # function name is first line of stdout
            if (result.stdout):
                lines = result.stdout.decode().split('\n')
                func = lines[0].strip()
            else: sys.error('unknown function at address {}'.format(addr))

            if (func != "??"):

                # Push last info
                stack.append((last_func, cur_stack_depth))
                last_func = func

                # Update
                cur_stack_depth += su_data[func]
                #print('func: "{}" = {}'.format(func, cur_stack_depth))
                if (cur_stack_depth > max_stack_depth):
                    max_stack_depth = cur_stack_depth
                    max_stack_trace = walk_stack()

                # Save info
                if (func in funcs_used):
                    funcs_used[func] += 1
                else:
                    funcs_used[func] = 1

                # end if (func != "??")

            # end if ('e')
        elif (words[0] == 'x'):
            # Only pop functions we pushed
            if (func in funcs_used):
                # Pop
                (last_func, cur_stack_depth) = stack.pop()
                #print('pop:  "{}" = {}'.format(last_func, cur_stack_depth))

print('Max Stack Depth = {}'.format(max_stack_depth))
print('Max Stack at: {}'.format(max_stack_trace))
