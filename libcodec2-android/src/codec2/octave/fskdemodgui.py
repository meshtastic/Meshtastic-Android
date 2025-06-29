#!/usr/bin/env python3
#
#	fsk_demod Statistics GUI
#	Accepts the stats output from fsk_demod on stdin, and plots it.
#
#	Mark Jessop 2016-03-13 <vk5qi@rfhead.net>
#
#	NOTE: This is intended to be run on a 'live' stream of samples, and hence expects
#	updates at about 10Hz. Anything faster will fill up the input queue and be discarded.
#
#	Call using: 
#	<producer>| ./fsk_demod --cu8 -s --stats=100 2 $SDR_RATE $BAUD_RATE - - 2> >(python fskdemodgui.py --wide) | <consumer>
#
#	Dependencies:
#	* Python (written for 2.7, only tested recently on 3+)
#	* numpy
#	* pyqtgraph
#	* PyQt5 (Or some Qt5 backend compatible with pyqtgraph)
#
#
import sys, time, json, argparse
from threading import Thread
try:
	from pyqtgraph.Qt import QtGui, QtCore
except ImportError:
	print("Could not import PyQt5 - is it installed?")
	sys.exit(1)

try:
	import numpy as np
except ImportError:
	print("Could not import numpy - is it installed?")
	sys.exit(1)

try:
	import pyqtgraph as pg
except ImportError:
	print("Could not import pyqtgraph - is it installed?")
	sys.exit(1)

try:
    # Python 2
    from Queue import Queue
except ImportError:
    # Python 3
    from queue import Queue

parser = argparse.ArgumentParser()
parser.add_argument("--wide", action="store_true", default=False, help="Alternate wide arrangement of widgets, for placement at bottom of 4:3 screen.")
args = parser.parse_args()

# Some settings...
update_rate = 2 # Hz
history_size = 100 # 10 seconds at 10Hz...
history_scale = np.linspace((-1*history_size+1)/float(update_rate),0,history_size)

# Input queue
in_queue = Queue(1) # 1-element FIFO... 

win = pg.GraphicsWindow()
win.setWindowTitle('FSK Demodulator Modem Statistics')


# Plot objects
ebno_plot = win.addPlot(title="Eb/No")
ppm_plot = win.addPlot(title="Sample Clock Offset")
if args.wide == False:
	win.nextRow()
else:
	win.resize(1024,200)
fest_plot =pg.PlotItem() # win.addPlot(title="Tone Frequency Estimation")
eye_plot = win.addPlot(title="Eye Diagram")
# Disable auto-ranging on eye plot and fix axes for a big speedup...
spec_plot = win.addPlot(title="Spectrum")
spec_plot.setYRange(0,40)
spec_plot.setLabel('left','SNR (dB)')
spec_plot.setLabel('bottom','FFT Bin')
# Configure plot labels and scales.
ebno_plot.setLabel('left','Eb/No (dB)')
ebno_plot.setLabel('bottom','Time (seconds)')
ebno_plot.setYRange(0,30)
ppm_plot.setLabel('left','Clock Offset (ppm)')
ppm_plot.setLabel('bottom','Time (seconds)')
fest_plot.setLabel('left','Frequency (Hz)')
fest_plot.setLabel('bottom','Time (seconds)')
eye_plot.disableAutoRange()
eye_plot.setYRange(0,1)
eye_plot.setXRange(0,15)
eye_xr = 15

# Data arrays...
ebno_data = np.zeros(history_size)
ppm_data = np.zeros(history_size)
fest_data = np.zeros((4,history_size))

# Curve objects, so we can update them...
spec_curve = spec_plot.plot([0])
ebno_curve = ebno_plot.plot(x=history_scale,y=ebno_data)
ppm_curve = ppm_plot.plot(x=history_scale,y=ppm_data)
fest1_curve = fest_plot.plot(x=history_scale,y=fest_data[0,:],pen='r') # f1 = Red
fest2_curve = fest_plot.plot(x=history_scale,y=fest_data[1,:],pen='g') # f2 = Blue
fest3_curve = fest_plot.plot(x=history_scale,y=fest_data[2,:],pen='b') # f3 = Greem
fest4_curve = fest_plot.plot(x=history_scale,y=fest_data[3,:],pen='m') # f4 = Magenta

# Plot update function. Reads from queue, processes and updates plots.
def update_plots():
	global timeout,timeout_counter,eye_plot,ebno_curve, ppm_curve, fest1_curve, fest2_curve, ebno_data, ppm_data, fest_data, in_queue, eye_xr, spec_curve

	try:
		if in_queue.empty():
			return
		in_data = in_queue.get_nowait()
		in_data = json.loads(in_data)
	except Exception as e:

		sys.stderr.write(str(e))
		return

	# Roll data arrays
	ebno_data[:-1] = ebno_data[1:]
	ppm_data[:-1] = ppm_data[1:]
	fest_data = np.roll(fest_data,-1,axis=1)


	# Try reading in the new data points from the dictionary.
	try:
		new_ebno = in_data['EbNodB']
		new_ppm = in_data['ppm']
		new_fest1 = in_data['f1_est']
		new_fest2 = in_data['f2_est']
		new_spec = in_data['samp_fft']
	except Exception as e:
		print("ERROR reading dict: %s" % e)

	# Try reading in the other 2 tones.
	try:
		new_fest3 = in_data['f3_est']
		new_fest4 = in_data['f4_est']
		fest_data[2,-1] = new_fest3
		fest_data[3,-1] = new_fest4
	except:
		# If we can't read these tones out of the dict, fill with NaN
		fest_data[2,-1] = np.nan
		fest_data[3,-1] = np.nan

	# Add in new data points
	ebno_data[-1] = new_ebno
	ppm_data[-1] = new_ppm
	fest_data[0,-1] = new_fest1
	fest_data[1,-1] = new_fest2


	# Update plots
	spec_data_log = 20*np.log10(np.array(new_spec)+0.01)
	spec_curve.setData(spec_data_log)
	spec_plot.setYRange(spec_data_log.max()-50,spec_data_log.max()+10)
	ebno_curve.setData(x=history_scale,y=ebno_data)
	ppm_curve.setData(x=history_scale,y=ppm_data)
	fest1_curve.setData(x=history_scale,y=fest_data[0,:],pen='r') # f1 = Red
	fest2_curve.setData(x=history_scale,y=fest_data[1,:],pen='g') # f2 = Blue
	fest3_curve.setData(x=history_scale,y=fest_data[2,:],pen='b') # f3 = Green
	fest4_curve.setData(x=history_scale,y=fest_data[3,:],pen='m') # f4 = Magenta

	#Now try reading in and plotting the eye diagram
	try:
		eye_data = np.array(in_data['eye_diagram'])

		#eye_plot.disableAutoRange()
		eye_plot.clear()
		col_index = 0
		for line in eye_data:
			eye_plot.plot(line,pen=(col_index,eye_data.shape[0]))
			col_index += 1
		#eye_plot.autoRange()
		
		#Quick autoranging for x-axis to allow for differing P and Ts values
		if eye_xr != len(eye_data[0]) - 1:
			eye_xr = len(eye_data[0]) - 1
			eye_plot.setXRange(0,len(eye_data[0])-1)
			
	except Exception as e:
		pass


timer = pg.QtCore.QTimer()
timer.timeout.connect(update_plots)
timer.start(1000/update_rate)


# Thread to read from stdin and push into a queue to be processed.
def read_input():
	global in_queue

	while True:
		in_line = sys.stdin.readline()
		if type(in_line) == bytes:
			in_line = in_line.decode()

		# Only push actual data into the queue...
		# This stops sending heaps of empty strings into the queue when fsk_demod closes.
		if in_line == "":
			time.sleep(0.1)
			continue

		if not in_queue.full():
			in_queue.put_nowait(in_line)


read_thread = Thread(target=read_input)
read_thread.daemon = True # Set as daemon, so when all other threads die, this one gets killed too.
read_thread.start()

## Start Qt event loop unless running in interactive mode or using pyside.
if __name__ == '__main__':
	import sys
	if (sys.flags.interactive != 1) or not hasattr(QtCore, 'PYQT_VERSION'):
		try:
			QtGui.QApplication.instance().exec_()
		except KeyboardInterrupt:
			sys.exit(0)
