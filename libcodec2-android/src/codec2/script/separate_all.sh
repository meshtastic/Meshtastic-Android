#!/bin/sh
sox $1.wav hts1_$1.wav trim 0 3
sox $1.wav hts2_$1.wav trim 3 3
sox $1.wav morig_$1.wav trim 6 2
sox $1.wav forig_$1.wav trim 8 2
sox $1.wav ve9qrp_$1.wav trim 10 9.5
sox $1.wav cq_ref_$1.wav trim 20 9
sox $1.wav kristoff_$1.wav trim 29.5 4
sox $1.wav vk5qi_$1.wav trim 33.5 13.5
sox $1.wav vk5dgr_$1.wav trim 47 10

