% power_from_gr.m
% David Rowe June 2018
%
% Measure power of signal from stdio, used for SNR tests from analog radios

#{
  $ rec -t raw -r 8000 -s -2 -c 1 - -q | octave --no-gui -qf power_from_stdio.m
#}

graphics_toolkit ("gnuplot")

Fs                = 48000; % sample rate in Hz
shorts_per_sample = 1;    % real samples

[s,c] = fread(stdin, shorts_per_sample*Fs, "short");

while c
  S = fft(s.*hanning(Fs));
  SdB = 20*log10(abs(S));
  figure(1); plot(real(s)); axis([0 Fs -3E4 3E4]);
  figure(2); plot(SdB); axis([0 12000 40 160]);

  printf("power: %f dB\n", 10*log10(var(s)));
  %pause(2);
  [s,c] = fread(stdin, shorts_per_sample*Fs, "short");
endwhile

