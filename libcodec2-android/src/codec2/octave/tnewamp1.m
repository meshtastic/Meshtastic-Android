% tnewamp1.m
%
% Copyright David Rowe 2017
% This program is distributed under the terms of the GNU General Public License 
% Version 2

#{

  Octave script to compare Octave and C versions of newamp1 processing, in order to test C port.

  c2sim -> dump files -> $ ../build_linux/unittest/tnewamp1 -> octave:1> tnewamp1
  Usage:

    1/ build codec2 with -DDUMP - see codec2-dev/README

    2/ Generate dump files using c2sim (just need to do this once)
       $ cd codec2-dev/build_linux/src
       $ ./c2sim ../../raw/hts1a.raw --phase0 --postfilter --dump hts1a --lpc 10 --dump_pitch_e hts1a_pitche.txt

    3/ Run C version which generates a file of Octave test vectors as output:

      $ cd codec2-dev/build_linux/unittest
      $ ./tnewamp1 ../../raw/hts1a.raw
            
    4/ Run Octave script to generate Octave test vectors and compare with C.

      octave:1> tnewamp1("../build_linux/src/hts1a")

    5/ Optionally listen to output

     ~/codec2-dev/build_linux/src$ ./c2sim ../../raw/hts1a.raw --phase0 --postfilter \
                                   --amread hts1a_am.out --hmread hts1a_hm.out \
                                   --Woread hts1a_Wo.out --hand_voicing hts1a_v.txt -o - \
                                      | play -q -t raw -r 8000 -s -2 -
#}

function tnewamp1(input_prefix, path_to_unittest="../build_linux/unittest/")
  printf("starting tnewamp1.c input_prefix: %s\n", input_prefix);

  visible_flag = 'off';
  newamp_700c;
  autotest;
  more off;
    
  max_amp = 80;
  postfilter = 0;   % optional postfiler that runs on Am, not used atm
  synth_phase = 1;

  if nargin == 1
    output_prefix = input_prefix;
  end
  model_name = strcat(input_prefix,"_model.txt");
  model = load(model_name);
  [frames nc] = size(model);

  voicing_name = strcat(input_prefix,"_pitche.txt");
  voicing = zeros(1,frames);
  
  if exist(voicing_name, "file") == 2
    pitche = load(voicing_name);
    voicing = pitche(:, 3);
  end

  % Load in C vectors and compare -----------------------------------------
 
  load(sprintf("%s/tnewamp1_out.txt", path_to_unittest));
  
  K = 20;
  [frames tmp] = size(rate_K_surface_c);
  [rate_K_surface sample_freqs_kHz] = resample_const_rate_f_mel(model(1:frames,:), K);

  melvq;
  load train_120_1.txt; load train_120_2.txt;
  train_120_vq(:,:,1)= train_120_1; train_120_vq(:,:,2)= train_120_2; m=5;
  m=5;

  eq = zeros(1,K);
  for f=1:frames
    mean_f(f) = mean(rate_K_surface(f,:));
    rate_K_surface_no_mean(f,:) = rate_K_surface(f,:) - mean_f(f);
    [rate_K_vec eq] = front_eq(rate_K_surface_no_mean(f,:), eq);
    rate_K_surface_no_mean(f,:) = rate_K_vec;
  end
  
  [res rate_K_surface_no_mean_ ind] = mbest(train_120_vq, rate_K_surface_no_mean, m);

  for f=1:frames
    rate_K_surface_no_mean_(f,:) = post_filter(rate_K_surface_no_mean_(f,:), sample_freqs_kHz, 1.5);
  end
    
  rate_K_surface_ = zeros(frames, K);
  interpolated_surface_ = zeros(frames, K);
  energy_q = create_energy_q;
  M = 4;
  for f=1:frames   
    [mean_f_ indx] = quantise(energy_q, mean_f(f));
    indexes(f,3) = indx - 1;
    rate_K_surface_(f,:) = rate_K_surface_no_mean_(f,:) + mean_f_;
  end

  % simulated decoder
  % break into segments of M frames.  We have 2 samples spaced M apart
  % and interpolate the rest.

  Nfft_phase = 128;  % note this needs to be 512 (FFT_ENC in codec2 if using --awread)
                     % with --hmread 128 is preferred as less memory/CPU
  model_ = zeros(frames, max_amp+2);
  voicing_ = zeros(1,frames);
  Aw = zeros(frames, Nfft_phase);
  H = zeros(frames, max_amp);
  model_(1,1) = Wo_left = 2*pi/100;
  voicing_left = 0;
  left_vec = zeros(1,K);

  % decoder runs on every M-th frame, 25Hz frame rate, offset at
  % start is to minimise processing delay (thanks Jeroen!)

  for f=M:M:frames   

    if voicing(f)
      index = encode_log_Wo(model(f,1), 6);
      if index == 0
        index = 1;
      end
      model_(f,1) = decode_log_Wo(index, 6);
    else
      model_(f,1) = 2*pi/100;
    end

    Wo_right = model_(f,1);
    voicing_right = voicing(f);
    [Wo_ avoicing_] = interp_Wo_v(Wo_left, Wo_right, voicing_left, voicing_right);

    #{
    for i=1:4
      fprintf(stderr, "  Wo: %4.3f L: %d v: %d\n", Wo_(i), floor(pi/Wo_(i)), avoicing_(i));
    end
    fprintf(stderr,"  rate_K_vec: ");
    for i=1:5
      fprintf(stderr,"%5.3f  ", rate_K_surface_(f,i));
    end
    fprintf(stderr,"\n");
    #}
    
    if f > M
      model_(f-M:f-1,1) = Wo_;
      voicing_(f-M:f-1) = avoicing_;
      model_(f-M:f-1,2) = floor(pi ./ model_(f-M:f-1,1)); % calculate L for each interpolated Wo
    end

    right_vec = rate_K_surface_(f,:);

    if f > M
      sample_points = [f-M f];
      resample_points = f-M:f-1;
      for k=1:K
        interpolated_surface_(resample_points,k) = interp_linear(sample_points, [left_vec(k) right_vec(k)], resample_points);
      end

      for k=f-M:f-1
        model_(k,:) = resample_rate_L(model_(k,:), interpolated_surface_(k,:), sample_freqs_kHz);
        Aw(k,:) = determine_phase(model_, k, Nfft_phase);
        for m=1:model_(k,2)
          b = round(m*model_(k,1)*Nfft_phase/(2*pi));  % map harmonic centre to DFT bin
          H(k,m) = exp(j*Aw(k, b+1));          
        end     
      end

   end
   
   % update for next time

   Wo_left = Wo_right;
   voicing_left = voicing_right;
   left_vec = right_vec;
   
  end
  
  f = figure(1); clf;
  mesh(angle(H));
  f = figure(2); clf;
  mesh(angle(H_c(:,1:max_amp)));
  f = figure(3); clf;
  mesh(abs(H - H_c(:,1:max_amp)));

  passes = 0; tests = 0;
  passes += check(eq, eq_c, 'Equaliser', 0.01); tests++;
  passes += check(rate_K_surface, rate_K_surface_c, 'rate_K_surface', 0.01); tests++;
  passes += check(mean_f, mean_c, 'mean', 0.01); tests++;
  passes += check(rate_K_surface_, rate_K_surface__c, 'rate_K_surface_', 0.01); tests++;
  passes += check(interpolated_surface_, interpolated_surface__c, 'interpolated_surface_', 0.01); tests++;
  passes += check(model_(:,1), model__c(:,1), 'interpolated Wo_', 0.001);  tests++;
  passes += check(voicing_, voicing__c, 'interpolated voicing'); tests++;
  passes += check(model_(:,3:max_amp+2), model__c(:,3:max_amp+2), 'rate L Am surface ', 0.1); tests++;
  passes += check(H, H_c(:,1:max_amp), 'phase surface'); tests++;
  printf("passes: %d fails: %d\n", passes, tests - passes);

  #{
  % Save to disk to check synthesis is OK with c2sim  

  output_prefix = input_prefix;
  Am_out_name = sprintf("%s_am.out", output_prefix);
  fam  = fopen(Am_out_name,"wb"); 

  Wo_out_name = sprintf("%s_Wo.out", output_prefix);
  fWo  = fopen(Wo_out_name,"wb"); 
  
  Aw_out_name = sprintf("%s_aw.out", output_prefix);
  faw = fopen(Aw_out_name,"wb"); 

  Hm_out_name = sprintf("%s_hm.out", output_prefix);
  fhm = fopen(Hm_out_name,"wb"); 

  printf("Generating files for c2sim: ");
  for f=1:frames
    printf(".", f);   
    Wo = model_(f,1);
    L = min([model_(f,2) max_amp-1]);
    Am = model_(f,3:(L+2));

    Am_ = zeros(1,2*max_amp);
    Am_(2:L) = Am(1:L-1);

    fwrite(fam, Am_, "float32");
    fwrite(fWo, Wo, "float32");

    % Note we send opposite phase as c2sim expects phase of LPC
    % analysis filter, just a convention based on historical
    % development of Codec 2

    Aw1 = zeros(1, Nfft_phase*2); 
    Aw1(1:2:Nfft_phase*2) = cos(Aw(f,:));
    Aw1(2:2:Nfft_phase*2) = -sin(Aw(f,:));
    fwrite(faw, Aw1, "float32");    

    Hm = zeros(1, 2*2*max_amp);
    for m=1:L
        Hm(2*m+1) = real(H(f,m));
        Hm(2*m+2) = imag(H(f,m));
    end    
    fwrite(fhm, Hm, "float32");    
  end

  fclose(fam); fclose(fWo); fclose(faw); fclose(fhm);

  v_out_name = sprintf("%s_v.txt", output_prefix);
  fv  = fopen(v_out_name,"wt"); 
  for f=1:length(voicing__c)
    fprintf(fv,"%d\n", voicing__c(f));
  end
  fclose(fv);
  #}
  
endfunction
 

