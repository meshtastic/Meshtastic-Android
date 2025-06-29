% fsk_basic.m
% David Rowe 30 sep 2016
%
% Basic non-coherent FSK modem simulation to illustrate principles
% and compare to ideal

rand('seed',1);
randn('seed',1);

Fs = 9600;     % sample rate
f1 = 1200;
f2 = 2400;
Rs = 1200;     % symbol rate
Ts = Fs/Rs;    % length of each symbol in samples
Nbits = 10000;
EbNodB = 9;    

tx_bits = round(rand(1,Nbits));

% continuous phase FSK modulator

w1 = 2*pi*f1/Fs;
w2 = 2*pi*f2/Fs;
tx_phase = 0;
tx = zeros(1,Ts*Nbits);

for i=1:Nbits
  for k=1:Ts
    if tx_bits(i)
      tx_phase += w2;
    else
      tx_phase += w1;
    end
    tx((i-1)*Ts+k) = exp(j*tx_phase);
  end
end

% AWGN channel noise

EbNo = 10^(EbNodB/10);
variance = Fs/(Rs*EbNo);
noise = sqrt(variance/2)*(randn(1,Nbits*Ts) + j*randn(1,Nbits*Ts));
rx = tx + noise;

% integrate and dump demodulator

rx_bits = zeros(1,Nbits);
for i=1:Nbits
  arx_symb = rx((i-1)*Ts + (1:Ts));
  filt1 = sum(exp(-j*w1*(1:Ts)) .* arx_symb);
  filt2 = sum(exp(-j*w2*(1:Ts)) .* arx_symb);
  rx_bits(i) = filt2 > filt1;
end

Nerrors = sum(xor(tx_bits, rx_bits));
ber = Nerrors/Nbits;
printf("EbNodB: %4.1f  Nerrors: %d BER: %1.3f\n", EbNodB, Nerrors, ber);



