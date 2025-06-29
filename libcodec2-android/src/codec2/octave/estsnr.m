% estsnr.m
% David Rowe May 2017
%
% estimate SNR of a sinewave in noise

function snr_dB = estsnr(x, Fs=8000, Nbw = 3000)

  [nr nc] = size(x);
  if nr == 1
    x = x';
  end

  % find peak in +ve side of spectrum, ignoring DC

  L = length(x);
  X = abs(fft(x));
  st = floor(0.05*L);  en = floor(0.45*L);
  [A mx_ind]= max(X(st:en));
  mx_ind += st;

  % signal energy might be spread by doppler, so sum energy
  % in frequencies +/- 1%

  s_st = floor(mx_ind*0.99); s_en = floor(mx_ind*1.01); 
  S = sum(X(s_st:s_en).^2);

  % real signal, so -ve power is the same

  S = 2*S;
  SdB = 10*log10(S);

  printf("Signal Power S: %3.2f dB\n", SdB);

  % locate a band of noise next to it and find power in band

  st = floor(mx_ind+0.05*(L/2));
  en = st + floor(0.1*(L/2));
  
  N = sum(X(st:en).^2);

  % scale this to obtain total noise power across total bandwidth

  N *= L/(en-st);
  NdB = 10*log10(N);
  printf("Noise Power N: %3.2f dB\n", NdB);

  % scale noise to designed noise bandwidth /2 fudge factor as its a
  % real signal, wish I had a better way to explain that!

  NodB = NdB - 10*log10(Fs/2);
  NscaleddB = NodB + 10*log10(Nbw);
  snr_dB = SdB - NscaleddB;

  figure(1); clf;
  plot(20*log10(X(1:L/2)),'b');
  hold on;
  plot([s_st s_en], [NdB NdB]- 10*log10(L), 'r');
  plot([st en], [NdB NdB]- 10*log10(L), 'r');
  hold off;
  top = 10*ceil(SdB/10);
  bot = NodB - 20;
  axis([1 L/2 bot top]);
  grid
  grid("minor")
endfunction
