% hackrf_dc.m
%
% David Rowe Nov 2015
%
% Downconverts a HackRF IQ sample file to a lower sample rate
%
% To sample a -60dB signal:
%   $ hackrf_transfer -r df1.iq -f 439200000  -n 10000000 -l 20 -g 40play file at 10.7MHz used:
%   octave:25> d = hackrf_dc("df1.iq")

function d = hackrf_dc(infilename)
  Fs1 = 10E6;   % input sample rate to HackRF
  Fs2 = 96E3;   % output sample rate
  fc  = 700E3;  % offset to shift input by, HackRF doesn't like signals in the centre
  
  s1 = load_hackrf(infilename);
  ls1 = length(s1);
  ls1 = 20*Fs1;
  t = 0:ls1-1;

  % shift down to baseband from Fc, not sure of rot90 rather than trasnpose operator '
  % to avoid unwanted complex conj

  s2 = rot90(s1(1:ls1)) .* exp(-j*2*pi*t*fc/Fs1);
  d = resample(s2, Fs2, Fs1);
end
