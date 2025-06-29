% hackrf_uc.m
%
% David Rowe Nov 2015
%
% Upconverts a real baseband sample file to a file suitable for input into a HackRF
%
% To play file at 10.7MHz used:
%   octave:25> hackrf_uc("fsk_10M.iq","fsk_horus_rx_1200_96k.raw")
%   $ hackrf_transfer -t ../octave/fsk_10M.iq -f 10000000 -a 1 -x 40

function hackrf_uc(outfilename, infilename)
  pkg load signal;
  Fs1 = 48E3;  % input sample rate
  Fs2 = 10E6;  % output sample rate to HackRF
  fc = 700E3-24E3;  % offset to shift to, HackRF doesn't like signals in the centre
  A  = 100;    % amplitude of signal after upc-nversion (max 127)
  N  = Fs1*20;
  
  fin = fopen(infilename,"rb");
  printf("1\n");
  s1 = fread(fin,"short");
  printf("1\n");
  fclose(fin);
  printf("1\n");
  ls1 = length(s1);
  printf("1\n");
  N = ls1;
  % single sided freq shifts, we don't want DSB
  printf("1\n");
  s1 = hilbert(s1(1:N)); 

  % upsample to Fs2

  M = Fs2/Fs1;
  s2 = resample(s1(1:N),Fs2,Fs1);
  ls2 = length(s2);
  mx = max(abs(s2));
  t = 0:ls2-1;
  printf("2\n");
  % shift up to Fc, note use of rot90 rather than trasnpose operator '
  % as we don't want complex conj, that would shift down in freq

  sout = rot90((A/mx)*s2) .* exp(j*2*pi*t*fc/Fs2);

  save_hackrf(outfilename,sout);
  
end
