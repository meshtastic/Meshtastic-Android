% newamp_700c.m
%
% Copyright David Rowe 2017
% This program is distributed under the terms of the GNU General Public License
% Version 2
%
% Library of Octave functions for rate K, mel spaced
% vector quantisation of spectral magnitudes used in Codec 2 700C mode.

1;
melvq; % mbest VQ functions

% --------------------------------------------------------------------------------
% Functions used by rate K mel work
% --------------------------------------------------------------------------------

% General 2nd order parabolic interpolator.  Used splines orginally,
% but this is much simpler and we don't need much accuracy.  Given two
% vectors of points xp and yp, find interpolated values y at points x

function y = interp_para(xp, yp, x)
  assert( (length(xp) >=3) && (length(yp) >= 3) );

  y = zeros(1,length(x));
  k = 1;
  for i=1:length(x)
    xi = x(i);

    % k is index into xp of where we start 3 points used to form parabola

    while ((xp(k+1) < xi) && (k < (length(xp)-2)))
      k++;
    end

    x1 = xp(k); y1 = yp(k); x2 = xp(k+1); y2 = yp(k+1); x3 = xp(k+2); y3 = yp(k+2);
    %printf("k: %d i: %d xi: %f x1: %f y1: %f\n", k, i, xi, x1, y1);

    a = ((y3-y2)/(x3-x2)-(y2-y1)/(x2-x1))/(x3-x1);
    b = ((y3-y2)/(x3-x2)*(x2-x1)+(y2-y1)/(x2-x1)*(x3-x2))/(x3-x1);

    y(i) = a*(xi-x2)^2 + b*(xi-x2) + y2;
  end
endfunction


% simple linear interpolator

function y = interp_linear(xp, yp, x)
  assert( (length(xp) == 2) && (length(yp) == 2) );

  m = (yp(2) - yp(1))/(xp(2) - xp(1));
  c = yp(1) - m*xp(1);

  y = zeros(1,length(x));
  for i=1:length(x)
    y(i) = m*x(i) + c;
  end
endfunction


% quantise input sample to nearest value in table, optionally return binary code

function [quant_out best_i bits] = quantise(levels, quant_in)

  % find closest quantiser level

  best_se = 1E32;
  for i=1:length(levels)
    se = (levels(i) - quant_in)^2;
    if se < best_se
      quant_out = levels(i);
      best_se = se;
      best_i = i;
    end
  end

  % convert index to binary bits

  numbits = ceil(log2(length(levels)));
  bits = zeros(1, numbits);
  for b=1:numbits
    bits(b) = bitand(best_i-1,2^(numbits-b)) != 0;
  end

endfunction


% Quantisation functions for Wo in log freq domain

function index = encode_log_Wo(Wo, bits)
    Wo_levels = 2.^bits;
    Wo_min = 2*pi/160;
    Wo_max = 2*pi/20;

    norm = (log10(Wo) - log10(Wo_min))/(log10(Wo_max) - log10(Wo_min));
    index = floor(Wo_levels * norm + 0.5);
    index = max(index, 0);
    index = min(index, Wo_levels-1);
endfunction


function Wo = decode_log_Wo(index, bits)
    Wo_levels = 2.^bits;
    Wo_min = 2*pi/160;
    Wo_max = 2*pi/20;

    step = (log10(Wo_max) - log10(Wo_min))/Wo_levels;
    Wo   = log10(Wo_min) + step*index;

    Wo = 10 .^ Wo;
endfunction


% convert index to binary bits

function bits = index_to_bits(value, numbits)
  levels = 2.^numbits;
  bits = zeros(1, numbits);
  for b=1:numbits
    bits(b) = bitand(value,2^(numbits-b)) != 0;
  end
end


function value = bits_to_index(bits, numbits)
  value = 2.^(numbits-1:-1:0) * bits;
endfunction


% Determine a phase spectra from a magnitude spectra
% from http://www.dsprelated.com/showcode/20.php
% Haven't _quite_ figured out how this works but have to start somewhere ....
%
% TODO: we may be able to sample at a lower rate, like mWo
%       but start with something that works

function [phase Gdbfk s Aw] = determine_phase(model, f, Nfft=512, ak)
  Fs      = 8000;
  max_amp = 80;
  L       = min([model(f,2) max_amp-1]);
  Wo      = model(f,1);

  sample_freqs_kHz = (Fs/1000)*[0:Nfft/2]/Nfft;           % fft frequency grid (nonneg freqs)
  Am = model(f,3:(L+2));
  AmdB = 20*log10(Am);
  rate_L_sample_freqs_kHz = (1:L)*Wo*4/pi;

  Gdbfk = interp_para(rate_L_sample_freqs_kHz, AmdB, sample_freqs_kHz);

  % optional input of aks for testing

  if nargin == 4
    Aw = 1 ./ fft(ak,Nfft);
    Gdbfk = 20*log10(abs(Aw(1:Nfft/2+1)));
  end

  [phase s] = mag_to_phase(Gdbfk, Nfft);

endfunction


% Non linear sampling of frequency axis, reducing the "rate" is a
% first step before VQ

function mel = ftomel(fHz)
  mel = floor(2595*log10(1+fHz/700)+0.5);
endfunction


function rate_K_sample_freqs_kHz = mel_sample_freqs_kHz(K)
  mel_start = ftomel(200); mel_end = ftomel(3700);
  step = (mel_end-mel_start)/(K-1);
  mel = mel_start:step:mel_end;
  rate_K_sample_freqs_Hz = 700*((10 .^ (mel/2595)) - 1);
  rate_K_sample_freqs_kHz = rate_K_sample_freqs_Hz/1000;
endfunction


function [rate_K_surface rate_K_sample_freqs_kHz] = resample_const_rate_f_mel(model, K)
  rate_K_sample_freqs_kHz = mel_sample_freqs_kHz(K);
  rate_K_surface = resample_const_rate_f(model, rate_K_sample_freqs_kHz);
endfunction


% Resample Am from time-varying rate L=floor(pi/Wo) to fixed rate K.  This can be viewed
% as a 3D surface with time, freq, and ampitude axis.

function [rate_K_surface rate_K_sample_freqs_kHz] = resample_const_rate_f(model, rate_K_sample_freqs_kHz)

  % convert rate L=pi/Wo amplitude samples to fixed rate K

  max_amp = 80;
  [frames col] = size(model);
  K = length(rate_K_sample_freqs_kHz);
  rate_K_surface = zeros(frames, K);

  for f=1:frames
    Wo = model(f,1);
    L = min([model(f,2) max_amp-1]);
    Am = model(f,3:(L+2));
    AmdB = 20*log10(Am);
    %pre = 10*log10((1:L)*Wo*4/(pi*0.3));
    %AmdB += pre;

    % clip between peak and peak -50dB, to reduce dynamic range

    AmdB_peak = max(AmdB);
    AmdB(find(AmdB < (AmdB_peak-50))) = AmdB_peak-50;

    rate_L_sample_freqs_kHz = (1:L)*Wo*4/pi;

    %rate_K_surface(f,:) = interp1(rate_L_sample_freqs_kHz, AmdB, rate_K_sample_freqs_kHz, "spline", "extrap");
    rate_K_surface(f,:)  = interp_para(rate_L_sample_freqs_kHz, AmdB, rate_K_sample_freqs_kHz);

    %printf("\r%d/%d", f, frames);
  end
  %printf("\n");
endfunction


% Take a rate K surface and convert back to time varying rate L

function [model_ AmdB_] = resample_rate_L(model, rate_K_surface, rate_K_sample_freqs_kHz)
  max_amp = 80;
  [frames col] = size(model);

  model_ = zeros(frames, max_amp+2);
  for f=1:frames
    Wo = model(f,1);
    L = model(f,2);
    rate_L_sample_freqs_kHz = (1:L)*Wo*4/pi;

    % back down to rate L

    % AmdB_ = interp1(rate_K_sample_freqs_kHz, rate_K_surface(f,:), rate_L_sample_freqs_kHz, "spline", 0);
    AmdB_ = interp_para([ 0 rate_K_sample_freqs_kHz 4], [0 rate_K_surface(f,:) 0], rate_L_sample_freqs_kHz);

    model_(f,1) = Wo; model_(f,2) = L; model_(f,3:(L+2)) = 10 .^ (AmdB_(1:L)/20);
   end
endfunction


% Post Filter, has a big impact on speech quality after VQ.  When used
% on a mean removed rate K vector, it raises formants, and supresses
% anti-formants.  As it manipulates amplitudes, we normalise energy to
% prevent clipping or large level variations.  pf_gain of 1.2 to 1.5
% (dB) seems to work OK.  Good area for further investigations and
% improvements in speech quality.

function vec = post_filter(vec, sample_freq_kHz, pf_gain = 1.5, voicing)
    % vec is rate K vector describing spectrum of current frame
    % lets pre-emp before applying PF. 20dB/dec over 300Hz

    pre = 20*log10(sample_freq_kHz/0.3);
    vec += pre;

    levels_before_linear = 10 .^ (vec/20);
    e_before = sum(levels_before_linear .^2);

    vec *= pf_gain;

    levels_after_linear = 10 .^ (vec/20);
    e_after = sum(levels_after_linear .^2);
    gain = e_after/e_before;
    gaindB = 10*log10(gain);
    vec -= gaindB;

    vec -= pre;
endfunction


% construct energy quantiser table, and save to text file to include in C

function energy_q = create_energy_q
    energy_q = 10 + 40/16*(0:15);
endfunction

function save_energy_q(fn)
  energy_q = create_energy_q;
  f = fopen(fn, "wt");
  fprintf(f, "1 %d\n", length(energy_q));
  for n=1:length(energy_q)
    fprintf(f, "%f\n", energy_q(n));
  end
  fclose(f);
endfunction


% save's VQ in format that can be compiled by Codec 2 build system

function save_vq(vqset, filenameprefix)
  [Nvec order stages] = size(vqset);
  for s=1:stages
    fn = sprintf("%s_%d.txt", filenameprefix, s);
    f = fopen(fn, "wt");
    fprintf(f, "%d %d\n", order, Nvec);
    for n=1:Nvec
      for k=1:order
        fprintf(f, "% 8.4f ", vqset(n,k,s));
      end
      fprintf(f, "\n");
    end
    fclose(f);
  end
endfunction


% Decoder side interpolation of Wo and voicing, to go from 25 Hz
% sample rate used over channel to 100Hz internal sample rate of Codec
% 2.

function [Wo_ voicing_] = interp_Wo_v(Wo1, Wo2, voicing1, voicing2)
    M = 4;
    max_amp = 80;

    Wo_ = zeros(1,M);
    voicing_ = zeros(1,M);
    if !voicing1 && !voicing2
       Wo_(1:M) = 2*pi/100;
    end

    if voicing1 && !voicing2
       Wo_(1:M/2) = Wo1;
       Wo_(M/2+1:M) = 2*pi/100;
       voicing_(1:M/2) = 1;
    end

    if !voicing1 && voicing2
       Wo_(1:M/2) = 2*pi/100;
       Wo_(M/2+1:M) = Wo2;
       voicing_(M/2+1:M) = 1;
    end

    if voicing1 && voicing2
      Wo_samples = [Wo1 Wo2];
      Wo_(1:M) = interp_linear([1 M+1], Wo_samples, 1:M);
      voicing_(1:M) = 1;
    end

    #{
    printf("f: %d f+M/2: %d Wo: %f %f (%f %%) v: %d %d \n", f, f+M/2, model(f,1), model(f+M/2,1), 100*abs(model(f,1) - model(f+M/2,1))/model(f,1), voicing(f), voicing(f+M/2));
    for i=f:f+M/2-1
      printf("  f: %d v: %d v_: %d Wo: %f Wo_: %f\n", i, voicing(i), voicing_(i), model(i,1),  model_(i,1));
    end
    #}
endfunction


% Equaliser in front of EQ, see vq_700c_eq.m for development version

function [rate_K_vec eq] = front_eq(rate_K_vec, eq)
  [tmp K] = size(rate_K_vec);
  ideal = [ 8 10 12 14 14*ones(1,K-1-4) -20];
  gain = 0.02;
  update = rate_K_vec - ideal;
  eq = (1-gain)*eq + gain*update;
  eq(find(eq < 0)) = 0;
endfunction
