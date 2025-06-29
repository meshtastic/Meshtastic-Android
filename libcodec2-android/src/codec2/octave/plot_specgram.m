% plot_specgram.m
% David Rowe May 2017
%
% As the name suggests.....

function S = plot_specgram(x, Fs=8000, fmin, fmax)
  
  step = fix(20*Fs/1000);     # one spectral slice every 5 ms
  window = fix(160*Fs/1000);  # 40 ms data window
  fftn = 2^nextpow2(window);  # next highest power of 2
  [S, f, t] = specgram(x, fftn, Fs, window, window-step);
  S = abs(S(2:fftn/2,:));     # magnitude in range 0<f<=Fs/2 Hz.
  S = S/max(S(:));            # normalize magnitude so that max is 0 dB.
  S = max(S, 10^(-20/10));    # clip below -20 dB.
  S = min(S, 10^(-3/10));     # clip above -3 dB.
  imagesc (t, f, log(S));     # display in log scale
  set (gca, "ydir", "normal"); # put the 'y' direction in the correct direction
  if nargin > 2
    axis([0 max(t) fmin fmax])
  end
  xlabel('Time (s)'); ylabel('Freq (Hz)');
endfunction
