% hackrf_twotone.m
%
% David Rowe Nov 2015
%
% Generates a two tone test signal that can be played out of HackRF
%
% To play file at 10.7MHz used:
%   $ hackrf_transfer -t ../octave/twotone.iq -f 10000000 -a 0 -x 47
%
% However 2nd harmonic at 21.4 was only -32dBC so not really useful for my application
% in testing an ADC

Fs = 8E6;
fc = 2E6;
f1 = fc;
f2 = fc+1E3;
A = 127;
T = 2;

N = T*Fs;
t = 0:N-1;
%s = A*exp(j*2*pi*t*f1/Fs) + A*exp(j*2*pi*t*f2/Fs);
s = A*exp(j*2*pi*t*f2/Fs);
save_hackrf("twotone.iq",s);
