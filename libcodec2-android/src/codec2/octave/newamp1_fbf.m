% newamp1_fbf.m
%
% Copyright David Rowe 2016
% This program is distributed under the terms of the GNU General Public License
% Version 2
%
% Interactive Octave script to explore frame by frame operation of newamp1
% spectral amplitude modelling.
%
% Usage:
%   Make sure codec2-dev is compiled with the -DDUMP option - see README for
%    instructions.
%   ~/codec2-dev/build_linux/src$ ./c2sim ../../raw/hts1a.raw --dump hts1a
%   $ cd ~/codec2-dev/octave
%   octave:14> newamp1_fbf("../build_linux/src/hts1a",50)


function newamp1_fbf(samname, f=73, varargin)
  more off;

  newamp_700c; melvq;
  load train_120_1.txt; load train_120_2.txt;
  train_120_vq(:,:,1)= train_120_1; train_120_vq(:,:,2)= train_120_2; m=5;

  Fs = 8000;  K = 20;

  vq = 0; eq_en = 0; pf = 0;

  % load up text files dumped from c2sim ---------------------------------------

  sn_name = strcat(samname,"_sn.txt");
  Sn = load(sn_name);
  sw_name = strcat(samname,"_sw.txt");
  Sw = load(sw_name);
  model_name = strcat(samname,"_model.txt");
  model = load(model_name);
  [frames tmp] = size(model);

  % pre-process
  [rate_K_surface sample_freqs_kHz] = resample_const_rate_f_mel(model(1:frames,:), K);

  % we need to know eq states on each frame
  eq = zeros(frames,K);  an_eq = zeros(1,K);
  for ff=1:frames
    mean_f = mean(rate_K_surface(ff,:));
    rate_K_vec_no_mean = rate_K_surface(ff,:) - mean_f;
    [tmp an_eq] = front_eq(rate_K_vec_no_mean, an_eq);
    eq(ff,:) = an_eq;
  end

  % Keyboard loop --------------------------------------------------------------

  k = ' ';
  do
    fg = 1;
    s = [ Sn(2*f-1,:) Sn(2*f,:) ];
    figure(fg++); clf; plot(s); axis([1 length(s) -20000 20000]);

    Wo = model(f,1); L = model(f,2); Am = model(f,3:(L+2)); AmdB = 20*log10(Am);
    Am_freqs_kHz = (1:L)*Wo*4/pi;

    % plots ----------------------------------

    figure(fg++); clf;
    l = sprintf(";rate %d AmdB;g+-", L);
    plot((1:L)*Wo*4000/pi, AmdB, l);
    axis([1 4000 -20 80]);
    hold on;
    stem(sample_freqs_kHz*1000, rate_K_surface(f,:), ";rate K;b+-");

    % default
    rate_K_vec_ = rate_K_surface(f,:);

    mean_f = mean(rate_K_surface(f,:));
    rate_K_vec_no_mean = rate_K_surface(f,:) - mean_f;
    if eq_en
      rate_K_vec_no_mean -= eq(f,:);
    end
    rate_K_vec_no_mean_ = rate_K_vec_no_mean;
    if vq
      [res rate_K_vec_no_mean_ ind] = mbest(train_120_vq, rate_K_vec_no_mean, m);
      if pf
        rate_K_vec_no_mean_ = post_filter(rate_K_vec_no_mean_, sample_freqs_kHz, 1.5);
      end
      rate_K_vec_ = rate_K_vec_no_mean_ + mean_f;
    end

    % back to rate L
    model_(f,:) = resample_rate_L(model(f,:), rate_K_vec_, sample_freqs_kHz);
    Am_ = model_(f,3:(L+2)); AmdB_ = 20*log10(Am_);
    varL = var(AmdB - AmdB_);

    plot((1:L)*Wo*4000/pi, AmdB_,";AmdB bar;r+-");
    l = sprintf(";error var %3.2f dB;bk+-", varL);
    plot((1:L)*Wo*4000/pi, (AmdB - AmdB_), l);
    hold off;

    figure(3); clf;
    plot(sample_freqs_kHz*1000, 40+ rate_K_vec_no_mean, ";rate K no mean;g+-");
    axis([1 4000 -20 80]); hold on;
    plot(sample_freqs_kHz*1000, 40 + rate_K_vec_no_mean_, ";rate K no mean bar;r+-");
    varK = var(rate_K_vec_no_mean - rate_K_vec_no_mean_);
    l = sprintf(";error var %3.2f dB;bk+-", varK);
    plot(sample_freqs_kHz*1000, rate_K_vec_no_mean - rate_K_vec_no_mean_, l);

    plot(sample_freqs_kHz*1000, eq(f,:), ";eq;b+-");
    hold off;

    % interactive menu ------------------------------------------

    printf("\rframe: %d  menu: n-next  b-back  q-quit  v-vq[%d] p-pf[%d] e-eq[%d]", f, vq, pf, eq_en);
    fflush(stdout);
    k = kbhit();

    if k == 'v'
       if vq == 0; vq = 1; else vq = 0; end
     endif
    if k == 'p'
       if pf == 0; pf = 1; else pf = 0; end
    endif
    if k == 'e'
       if eq_en == 0; eq_en = 1; else eq_en = 0; end
    endif
    if k == 'n'
      f = f + 1;
    endif
    if k == 'b'
      f = f - 1;
    endif

  until (k == 'q')
  printf("\n");

endfunction


function ind = arg_exists(v, str)
   ind = 0;
   for i=1:length(v)
      if !ind && strcmp(v{i}, str)
        ind = i;
      end
    end
endfunction
