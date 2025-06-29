% phase_noise.m
% David Nov 2019

% Close-in look at phase noise.  Feed in a off-air sample file of a
% sine wave, extracts the phase noise countour and returns the Doppler
% spreading function that can be used to model the channel in
% simulations

function spread_FsHz = phase_noise(file_name)
  Fs = 8000;
  s = load_raw(file_name);
  % skip past wave header
  s = [zeros(256,1); s(256:end)];
  S = abs(fft(s(1:Fs).*hanning(Fs)));
  [mx mx_bin] = max(S);
  ftone = mx_bin-1;
  
  figure(1); clf;
  plot(20*log10(S(1:Fs/2)))
  title('Input Spectrum');
  
  % downshift to baseband and LPF.  We just want the sinusoid with as little
  % additive AWGN noise as possible
  sbb = s' .* exp(-j*(1:length(s))*2*pi*ftone/Fs);
  [b a] = cheby1(4, 1, 20/Fs);
  sbb_lpf = filter(b,a,sbb);

  spread_fsHz = sbb_lpf;
  
  % estimate and remove fine freq offset, and HF phase noise
  
  st = Fs; en = 20*Fs;
  phase = unwrap(angle(sbb_lpf(st:en)));
  fine_freq = mean(phase(2:end) - phase(1:end-1));
  sbb_lpf_fine = sbb_lpf .* exp(-j*(1:length(sbb_lpf))*fine_freq);
  phase = unwrap(angle(sbb_lpf_fine(st:en)));

  printf("length: %3.2fs freq: %5.1f\n", length(s)/Fs, ftone+fine_freq*Fs/(2*pi));

  figure(2); clf;
  plot3((st:en)/Fs, real(sbb_lpf_fine(st:en)),imag(sbb_lpf_fine(st:en)))
  title('Polar phase trajectory');

  figure(3); clf;
  S2 = fftshift(fft(sbb_lpf_fine(Fs:Fs*11)));
  [mx mx_bin] = max(abs(S2));
  S2dB = 20*log10(abs(S2));
  mxdB = 10*ceil(max(S2dB)/10);
  x = -10:0.1:10;
  plot(x,S2dB(mx_bin-100:mx_bin+100));
  axis([-10 10 mxdB-40 mxdB])
  title('Close in Phase Noise Spectrum');
  xlabel('Freq (Hz)');
  grid;
  
  figure(5); clf;
  t = (st:en)/Fs;
  plot(t, phase,'b;phase;');
  title('Unwrapped Phase');
  xlabel('Time (sec)')
  ylabel('Phase (radians)')
  
  figure(6); clf;
  beta = 0.00001;
  rate_of_change_Hz = filter(beta, [1 -(1-beta)],phase(2:end) - phase(1:end-1))*Fs/pi;
  plot(t(2:end), rate_of_change_Hz)
  title('Rate of change of phase (Hz)');
  xlabel('Time (sec)')
  ylabel('Freq (Hz)')

  spread_FsHz = sbb_lpf_fine/std(sbb_lpf_fine);
end
