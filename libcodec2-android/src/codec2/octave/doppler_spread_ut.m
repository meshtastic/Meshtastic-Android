% doppler_spread_ut.m
% David Rowe Jan 2016
%
% Unit test script for doppler_spread

f = 1;
Fs = 8000;
N  = Fs*10;

[spread states] = doppler_spread(f, Fs, N);

% use spreading samples to modulate 1000Hz sine wave
% You can listen to this with: sine1k_1Hz.raw

%   $ play -t raw -r 8000 -s -2 
s = cos(2*pi*(1:N)*1000/Fs);
s = s .* spread;
s = real(s)*5000;
fs = fopen("sine1k_1Hz.raw","wb"); fwrite(fs,s,"short"); fclose(fs);

% Some plots

x = states.x; y = states.y; b = states.b;

H = freqz(b,1,x);

figure(1)
clf
subplot(211)
plot(x,y,';target;')
title('Gaussian Filter Freq Resp Lin');
legend('boxoff');
subplot(212)
plot(x,20*log10(y),';target;')
hold on;
plot(x,20*log10(y),'g+;actual;')
hold off;
axis([0 f*10/2 -60 0])
title('Gaussian Filter Freq Resp dB');
xlabel('Freq (Hz)');
legend('boxoff');

figure(2);
subplot(211)
plot(abs(spread))
title('Spreading Function Magnitude');
subplot(212)
plot(s)
title('1000Hz Sine Wave');
xlabel('Time (samples)')

