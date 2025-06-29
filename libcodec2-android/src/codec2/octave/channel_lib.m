% channel.m
%
% Commonly used channel simulation functions

1;

function [spread1 spread2 path_delay_samples] = channel_multipath(channel, Fs, Nsam)
    % Winlink multipath definitions
    if strcmp(channel, 'mpg')     dopplerSpreadHz = 0.1; path_delay_ms = 0.5;
    elseif strcmp(channel, 'mpm') dopplerSpreadHz = 0.5; path_delay_ms = 1.0;
    elseif strcmp(channel, 'mpp') dopplerSpreadHz = 1.0; path_delay_ms = 2.0;
    elseif strcmp(channel, 'mpd') dopplerSpreadHz = 2.0; path_delay_ms = 4.0;
    elseif strcmp(channel, 'mpf') dopplerSpreadHz = 4.0; path_delay_ms = 4.0;
    elseif strcmp(channel, 'notch') dopplerSpreadHz = 0.0; path_delay_ms = 2.0;
    elseif printf("Unknown multipath channel\n"); assert(0); end

    path_delay_samples = path_delay_ms*Fs/1000;
    %printf(" Doppler Spread: %3.2f Hz Path Delay: %3.2f ms %d samples\n", dopplerSpreadHz, path_delay_ms, path_delay_samples);

    if strcmp(channel, "notch")
      % simple notch filter (not time varying), hand tweaked to be 10dB down at about 1300 Hz (Fc-200Hz)
      spread1 = 0.5*ones(1,Nsam);
      spread2 = j*0.2*ones(1,Nsam);
    else
      % generate same fading pattern for every run
      spread1 = doppler_spread(dopplerSpreadHz, Fs, Nsam);
      spread2 = doppler_spread(dopplerSpreadHz, Fs, Nsam);
    end
    
    % sometimes doppler_spread() doesn't return exactly the number of samples we need
    if length(spread1) < Nsam
      printf("not enough doppler spreading samples %d %d\n", length(spread1), Nsam);
      assert(0);
    end
    if length(spread2) < Nsam
      printf("not enough doppler spreading samples %d %d\n", length(spread2), Nsam);
      assert(0);
    end
endfunction

% returns real rx signal with noise added, input is complex tx signal
function [rx_real rx sigma] = channel_simulate(Fs, SNR3kdB, freq_offset_Hz, channel, tx, verbose=0)
  Nsam = length(tx);
  rx = tx;

  if strcmp(channel, 'awgn') == 0
    [spread1 spread2 path_delay_samples] = channel_multipath(channel, Fs, Nsam);
    rx  = tx(1:Nsam) .* spread1(1:Nsam);
    rx += [zeros(1,path_delay_samples) tx(1:Nsam-path_delay_samples)] .* spread2(1:Nsam);
  end

  woffset = 2*pi*freq_offset_Hz/Fs;
  rx = rx .* exp(j*woffset*(1:Nsam));

  rx_real = real(rx); S = rx_real*rx_real';
  rpapr = 10*log10(max(abs(rx_real).^2)/mean(abs(rx_real).^2));

  % SNR in a 4k bandwidth will be lower than 3k as total noise power N is higher
  SNR4kdB = SNR3kdB - 10*log10(Fs/2) + 10*log10(3000); SNR = 10^(SNR4kdB/10);
  N = S/SNR; sigma = sqrt(N/Nsam);
  n = sigma*randn(1,Nsam);
  % printf("SNR3kdB: %f SNR4kdB: %f N: %f %f\n", SNR3kdB, SNR4kdB, N, n*n');
  rx_real += n;
  % check our sums are OK to within 0.25 dB
  SNR4kdB_measured = 10*log10(S/(n*n')); 
  assert (abs(SNR4kdB - SNR4kdB_measured) < 0.5);
  if verbose
    printf("foff: %3.1f Hz SNR(3k): %3.1f dB  ", freq_offset_Hz, SNR3kdB);
    printf("measSNR3k: %3.2f dB N: %3.2f dB\n",
           10*log10(S/(n*n')) + 10*log10(4000) - 10*log10(3000), 10*log10(n*n'));
  end
endfunction
