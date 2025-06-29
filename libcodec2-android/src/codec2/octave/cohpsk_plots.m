% cohpsk_plots.m
% David Rowe Feb 2017
%
% Generate some plots for chps modem blog post

rand("seed",1);

% Multipath with simple unfiltered BPSK signal

N = 100;  % number of symbols
M = 4;    % oversample rate

tx_bits = rand(1,N) > 0.5;
tx_symbols = 2*tx_bits - 1;
tx = zeros(1,N*M);

for i=1:N
  tx((i-1)*M+1:i*M) = tx_symbols(i);
end

h = [0 0 0 0 0.5];   % model of second path

rx1 = tx;
rx2 = filter(h,1,tx);
rx = rx1 + rx2;

% Multipath in time domain

figure(1); clf;
subplot(311)
plot(rx1, "linewidth", 4)
axis([0 10*M+1 -2 2]);
subplot(312)
plot(rx2, "linewidth", 4)
axis([0 10*M+1 -2 2]);
subplot(313)
plot(rx, "linewidth", 4)
axis([0 10*M+1 -2 2]);
xlabel('Time');
print("cohpsk_multipath_time.png", "-dpng", "-S600,440", "-F:8")

% Multipath channel magnitude and phase response against frequency

h = [1 0 0 0 0.5];   % model of two path multipath channel
H = freqz(h,1,100);

figure(2); clf;
subplot(211)
plot(20*log10(abs(H)), "linewidth", 4)
title('Amplitude (dB)');
subplot(212)
plot(angle(H), "linewidth", 4)
title('Phase (rads)');
%axis([0 500 -2 2]);
xlabel('Frequency');
print("cohpsk_multipath_channel.png", "-dpng", "-S600,440", "-F:8")

% Effective of 1 sample multipath for different symbols lengths

h = [1 0 0 0 0.5];   % model of two path multipath channel
M1 = 2;
M2 = 20;
tx1 = zeros(1,N*M1);
tx2 = zeros(1,N*M2);
for i=1:N
  tx1((i-1)*M1+1:i*M1) = tx_symbols(i);
  tx2((i-1)*M2+1:i*M2) = tx_symbols(i);
end

rx1 = filter(h,1,tx1);
rx2 = filter(h,1,tx2);

figure(3); clf;
subplot(211)
plot(rx1, "linewidth", 4)
axis([0 10*M1+1 -2 2]);
title('1ms multipath with 2ms symbols')
subplot(212)
plot(rx2, "linewidth", 4)
axis([0 10*M2+1 -2 2]);
title('1ms multipath with 20ms symbols')
xlabel('Time');
print("cohpsk_multipath_symbol_length.png", "-dpng", "-S600,440", "-F:8")

% DBPSK --------------------------------------------------

N = 10;
tx_bits = rand(1,N) > 0.5;
bpsk = 2*tx_bits - 1;
prev_bpsk = 1;
for i=1:N

  % BPSK -> DBPSK

  dbpsk(i) = bpsk(i) * (-prev_bpsk);
  prev_bpsk = bpsk(i);

  % oversampling

  tx_bpsk((i-1)*M+1:i*M) = bpsk(i);
  tx_dbpsk((i-1)*M+1:i*M) = dbpsk(i);
end

figure(4); clf;
subplot(211);
plot(tx_bpsk, "linewidth", 4)
axis([0 10*M+1 -2 2]);
title('Tx BPSK');
subplot(212);
plot(tx_dbpsk, "linewidth", 4)
axis([0 10*M+1 -2 2]);
title('Tx DBPSK');
print("cohpsk_dbpsk1.png", "-dpng", "-S600,440", "-F:8")

dbpsk *= -1;

prev_rx = 1;
for i=1:N

  % rx DBPSK -> PSK

  bpsk(i) = dbpsk(i) * (prev_rx);
  prev_rx = bpsk(i);

  % oversampling

  rx_bpsk((i-1)*M+1:i*M) = bpsk(i);
  rx_dbpsk((i-1)*M+1:i*M) = dbpsk(i);
end

figure(5); clf;
subplot(211);
plot(rx_dbpsk, "linewidth", 4)
axis([0 10*M+1 -2 2]);
title('Rx DBPSK with 180 deg phase shift');
subplot(212);
plot(rx_bpsk, "linewidth", 4)
axis([0 10*M+1 -2 2]);
title('Rx BPSK');
print("cohpsk_dbpsk2.png", "-dpng", "-S600,440", "-F:8")

