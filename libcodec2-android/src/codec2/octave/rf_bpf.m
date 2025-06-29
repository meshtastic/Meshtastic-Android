% rtlsdr_bpf.m
%
% David Rowe 24 August 2018
%
% Calculate component values for cascaded HP-LP 2-8 MHz Chebychev filter
%
% From "RF Circuit Design", Chris Bowick, Ch 3

1;

function C = find_C(Cn, fc, R)
  C = Cn/(2*pi*fc*R);
endfunction

function L = find_L(Ln, fc, R)
  L = R*Ln/(2*pi*fc);
endfunction

% 3rd order HP filter, 1dB ripple Cheby, 3MHz cut off, >20dB down at
% 1MHz to nail stong AM broadcast signals, Table 3-7A. Use a Rs=50,
% Rl=50, so Rs/Rl = 1.  Note we assume a or phantom load in between
% cascaded HP-LP sections of 50 ohms.

L1 = find_L(1/2.216, 3E6, 50);
C1 = find_C(1/1.088, 3E6, 50);
L2 = find_L(1/2.216, 3E6, 50);

printf("L1: %f uH C1: %f pF L2: %f uH\n", L1*1E6, C1*1E12, L2*1E6);

% 3rd order LPF, 8MHz cut off so >30dB down at 21MHz, which aliases back to 7MHz
% with Fs=28MHz on RTLSDR (14 MHz Nyquist freq). Rs=50, Rl=50, Rs/Rl = 1

C2 = find_C(2.216, 9E6, 50);
L3 = find_L(1.088, 9E6, 50);
C3 = find_C(2.216, 9E6, 50);

printf("C2: %f pF L3: %f uH C3: %f pF\n", C2*1E12, L3*1E6, C3*1E12);
