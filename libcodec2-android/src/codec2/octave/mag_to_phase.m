% mag_to_phase.m
% 
% David Rowe Sep 2015
%
% Slighly modified version of http://www.dsprelated.com/showcode/20.php
%
% Given a magnitude spectrum in dB, returns a minimum-phase phase
% spectra.  Both must be sampled at a Nfft. My understanding of this
% is rather dim, but a working example is good place to start!


function [phase s] = mag_to_phase(Gdbfk, Nfft = 512, verbose_en = 0)

  Ns = length(Gdbfk); if Ns~=Nfft/2+1, error("confusion"); end
  Sdb = [Gdbfk,Gdbfk(Ns-1:-1:2)]; % install negative-frequencies

  S = 10 .^ (Sdb/20); % convert to linear magnitude
  s = ifft(S);        % desired impulse response
  s = real(s);        % any imaginary part is quantization noise
  tlerr = 100*norm(s(round(0.9*Ns:1.1*Ns)))/norm(s);
  if verbose_en
    disp(sprintf(['  Time-limitedness check: Outer 20%% of impulse ' ...
               'response is %0.2f %% of total rms'],tlerr));
  end
  % = 0.02 percent

  if verbose_en
    if tlerr>1.0 % arbitrarily set 1% as the upper limit allowed
      disp('  Increase Nfft and/or smooth Sdb\n');
    end
  end

  c = ifft(Sdb); % compute real cepstrum from log magnitude spectrum
 
  % Check aliasing of cepstrum (in theory there is always some):

  caliaserr = 100*norm(c(round(Ns*0.9:Ns*1.1)))/norm(c);
  if verbose_en
    disp(sprintf(['  Cepstral time-aliasing check: Outer 20%% of ' ...
                 'cepstrum holds %0.2f %% of total rms\n'],caliaserr));
  end

  if verbose_en
    if caliaserr>1.0 % arbitrary limit
      disp('  Increase Nfft and/or smooth Sdb to shorten cepstrum\n');
    end
  end

  % Fold cepstrum to reflect non-min-phase zeros inside unit circle:

  cf = [c(1), c(2:Ns-1)+c(Nfft:-1:Ns+1), c(Ns), zeros(1,Nfft-Ns)];

  Cf = fft(cf); % = dB_magnitude + j * minimum_phase

  % The maths says we are meant to be using log(x), not 20*log10(x),
  % so we need to scale the phase to account for this:
  % log(x) = 20*log10(x)/scale;

  scale = (20/log(10));
  phase = imag(Cf)/scale;
endfunction

