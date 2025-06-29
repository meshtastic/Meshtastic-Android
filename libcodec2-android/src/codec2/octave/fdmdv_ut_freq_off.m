% fdmdv_ut_freq_off.m
% David Rowe 17 June 2014
%
% Unit Test program for freq offset estimation in FDMDV modem.  
%
% Copyright David Rowe 2012 This program is
% distributed under the terms of the GNU General Public License
% Version 2

% [ ] sweep of different delays
% [ ] sweep of Eb/No
% [ ] sweep of freq offsets
% [ ] step change in foff
%     + time to respond
% [ ] plot/print pass fail/relevant stats
%      + variance
%      + histogram of freq ests?

fdmdv;  % load modem code
hf_sim; % load hf sim code

% ---------------------------------------------------------------------
% Eb/No calculations.  We need to work out Eb/No for each FDM carrier.
% Total power is sum of power in all FDM carriers.  These calcs set the
% Eb/No of the data carriers, Eb/No of pilot will be higher.
% ---------------------------------------------------------------------

function [Nsd SNR] = calc_Nsd_from_EbNo(EbNo_dB)
  global Rs;
  global Nb;
  global Nc;
  global Fs;

  C = 1; % power of each FDM carrier (energy/sample).  Total Carrier power should = Nc*C = Nc
  N = 1; % total noise power (energy/sample) of noise source across entire bandwidth

  % Eb  = Carrier power * symbol time / (bits/symbol)
  %     = C *(1/Rs) / Nb
  Eb_dB = 10*log10(C) - 10*log10(Rs) - 10*log10(Nb);

  No_dBHz = Eb_dB - EbNo_dB;

  % Noise power = Noise spectral density * bandwidth
  % Noise power = Noise spectral density * Fs/2 for real signals
  N_dB = No_dBHz + 10*log10(Fs/2);
  Ngain_dB = N_dB - 10*log10(N);
  Nsd = 10^(Ngain_dB/20);

  % C/No = Carrier Power/noise spectral density
  %      = power per carrier*number of carriers / noise spectral density
  CNo_dB = 10*log10(C) + 10*log10(Nc) - No_dBHz;

  % SNR in equivalent 3000 Hz SSB channel, adding extra power for pilot to get
  % true SNR.

  B = 3000;
  SNR = CNo_dB - 10*log10(B) + 10*log10((Nc+4)/Nc);
end

% we keep a m sample buffer in sample_memory
% update sample_memory with n samples each time this function is called
% outputs one nfft2 slice of spectrogram in dB.  Good idea to make nfft2 a power of 2

function [S, states_out] = spectrogram_update(samples, n, states_in)
  sample_memory = states_in.sample_memory;
  m             = states_in.m;
  nfft2         = states_in.nfft2;
  lower_clip_dB = states_in.lower_clip_dB;
  dec           = states_in.dec;

  sample_memory(1:m-n)   = sample_memory(n+1:m);
  sample_memory(m-n+1:m) = samples;

  F = fft(sample_memory .* hanning(m)', 2*nfft2);
  S = 20*log10(abs(F(1:dec:nfft2))/(nfft2));
  S(find(S < lower_clip_dB)) = lower_clip_dB;    % clip lower limit

  states_out = states_in;
  states_out.sample_memory = sample_memory;
end

% ------------------------------------------------------------

function sim_out = freq_off_est_test(sim_in)
  global Nc;
  global Nb;
  global M;
  global Fs;
  global pilot_lut_index;
  global prev_pilot_lut_index;
  global pilot_lpf1;
  global Npilotlpf;
  global spread;
  global spread_2ms;
  global hf_gain;

  EbNovec   = sim_in.EbNovec;
  Ndelay    = sim_in.delay;
  frames    = sim_in.frames;
  startup_delay = sim_in.startup_delay;
  allowable_error = sim_in.allowable_error;
  foff_hz   = sim_in.foff_hz;
  hf_sim    = sim_in.hf_sim;
  hf_delay  = floor(sim_in.hf_delay_ms*Fs/1000);
  plot_type = sim_in.plot_type;

  % work out gain for HF model
  % e = sum((g*s)^2) = g*g*sum(s^2) = N, g = sqrt(N/sum(s^2))
  % compute so e=N

  s1 = spread(1:frames*M);
  s2 = [zeros(hf_delay,1); spread_2ms(1:frames*M)];
  s2 = s2(1:frames*M);

  p = (s1+s2)'*(s1+s2);
  hf_gain = sqrt(frames*M/p);
  p2 = (hf_gain*(s1+s2))'*(hf_gain*(s1+s2));

  if hf_sim
    channel_model = "HF";
  else
    channel_model = "AWGN";
  end

  % spectrogram states

  spec_states.m             = 8*M;
  spec_states.nfft2         = 2 ^ ceil(log2(spec_states.m/2));
  spec_states.dec           = 4;
  spec_states.sample_memory = zeros(1, spec_states.m);
  spec_states.lower_clip_dB = -30;

  printf("\n%s\n", sim_in.test_name);
  printf("  Channel EbNo SNR(calc) SNR(meas) SD(Hz) Hits Hits(%%) Result\n");

  % ---------------------------------------------------------------------
  % Main loop 
  % ---------------------------------------------------------------------

  for ne = 1:length(EbNovec)
    EbNo_dB = EbNovec(ne);
    [Nsd SNR] = calc_Nsd_from_EbNo(EbNo_dB);
    hits = 0;

    tx_filt = zeros(Nc,M);
    prev_tx_symbols = ones(Nc+1,1);

    tx_fdm_log = [];
    rx_fdm_log = [];
    pilot_lpf1_log = [];
    S1_log = [];
    rx_fdm_delay = zeros(M+Ndelay,1);

    % freq offset simulation states

    phase_offset = 1;
    Nmedian = 20;
    foff_median=zeros(1,Nmedian);

    % hf sim states
    
    path2 = zeros(1,hf_delay+M);
    sum_sig   = 0;
    sum_noise = 0;

    % state machine
    state = 0;
    fest_current = 0;
    fdelta = 5;
    candidate_thresh = 10;
    foff_est_thresh_prev = 0;

    for f=1:frames

      % ------------------- Modulator -------------------

      tx_bits = get_test_bits(Nc*Nb); 
      tx_symbols = bits_to_psk(prev_tx_symbols, tx_bits, 'dqpsk'); 

      % simulate BPF filtering of +/- 200 Hz
      % tx_symbols(1:6) = 0; tx_symbols(9:Nc) = 0;

      prev_tx_symbols = tx_symbols; 
      tx_baseband = tx_filter(tx_symbols); 
      tx_fdm = fdm_upconvert(tx_baseband);
      tx_fdm_log = [tx_fdm_log real(tx_fdm)];

      % ------------------- Channel simulation -------------------

      % frequency offset

      for i=1:M 
        freq_offset = exp(j*2*pi*foff_hz(f)/Fs);
        phase_offset *= freq_offset; 
        tx_fdm(i) = phase_offset*tx_fdm(i); 
      end

      % optional HF channel sim

      if hf_sim
        path1 = tx_fdm .* conj(spread(f*M+1:f*M+M)');

        path2(1:hf_delay) = path2(M+1:hf_delay+M);
        path2(hf_delay+1:hf_delay+M) = tx_fdm .* conj(spread_2ms(f*M+1:f*M+M)');

        tx_fdm = hf_gain*(path1 + path2(1:M));
      end
      sum_sig += tx_fdm * tx_fdm';
      
      rx_fdm = real(tx_fdm);

      % AWGN noise

      noise = Nsd*randn(1,M); 
      sum_noise += noise * noise';
      rx_fdm += noise; 
      rx_fdm_log = [rx_fdm_log rx_fdm];

      % Fixed Delay

      rx_fdm_delay(1:Ndelay) = rx_fdm_delay(M+1:M+Ndelay);
      rx_fdm_delay(Ndelay+1:M+Ndelay) = rx_fdm; 

      % ------------------- Freq Offset Est -------------------

      % frequency offset estimation and correction, need to call
      % rx_est_freq_offset even in track mode to keep states updated

      [pilot prev_pilot pilot_lut_index prev_pilot_lut_index] = ...
          get_pilot(pilot_lut_index, prev_pilot_lut_index, M); 
      [foff_est S1 S2] = rx_est_freq_offset(rx_fdm_delay, pilot, prev_pilot, M);
      pilot_lpf1_log = [pilot_lpf1_log pilot_lpf1(Npilotlpf-M+1:Npilotlpf)];
      S1_log(f,:) = fftshift(S1);
      S2_log(f,:) = fftshift(S2);
     
      % raw estimate
      
      foff_log(ne,f) = foff_est;
      maxS1_log(ne,f) = max(S1.*conj(S1)/(S1*S1'));
      maxS2_log(ne,f) = max(S2.*conj(S2)/(S2*S2'));

      % median filter post-processed

      foff_median(1:Nmedian-1) = foff_median(2:Nmedian);
      foff_median(Nmedian) = foff_est;
      foff_median_log(ne,f) = foff_coarse = median(foff_median);

      % state machine post-processed

      next_state = state;
      if state == 0
        if abs(foff_est - fest_current) > fdelta
          fest_candidate = foff_est;
          candidate_count = 0; 
          next_state = 1;
        end
      end
      if state == 1
         if abs(foff_est - fest_candidate) > fdelta
           next_state = 0;
         end
        candidate_count++; 
         if candidate_count > candidate_thresh
           fest_current = fest_candidate;
           next_state = 0;
        end
      end
      state = next_state;
      foff_statemach_log(ne,f) = fest_current;

      % threshold post processed

      if (maxS1_log(ne,f) > 0.06) || (maxS2_log(ne,f) > 0.06)
      %if (maxS1_log(ne,f) > 0.08)
         foff_thresh_log(ne,f) = foff_est;
      else   
         foff_thresh_log(ne,f) = foff_est_thresh_prev;
      end
      foff_est_thresh_prev = foff_thresh_log(ne,f);

      % hit/miss stats
      fest_current = foff_statemach_log(ne,f);
      if (f > startup_delay) && (abs(fest_current - foff_hz(f)) < allowable_error)
        hits++;
      end

      if length(EbNovec) == 1
        [spectrogram(f,:) spec_states] = spectrogram_update(rx_fdm, M, spec_states);
      end
    end

    % results for this EbNo value

    sim_out.foff_sd(ne) = std(foff_log(ne,startup_delay:frames));
    sim_out.hits = hits;
    sim_out.hits_percent = 100*sim_out.hits/(frames-startup_delay);
    sim_out.SNRvec(ne) = SNR;
    sim_out.tx_fdm_log = tx_fdm_log;
    sim_out.rx_fdm_log = rx_fdm_log;

    % noise we have measures is 4000 Hz wide, we want noise in 3000 Hz BW

    snr_meas = 10*log10(sum_sig/(sum_noise*4000/3000));

    printf("  %6s %5.2f % -5.2f     % -5.2f     %3.2f   %d  %3.2f  ", ...
           channel_model, EbNo_dB, SNR, snr_meas, sim_out.foff_sd(ne), sim_out.hits, sim_out.hits_percent);
 
    if sim_out.hits_percent == 100
      printf("PASS\n");
    else
      printf("FAIL\n");
      figure(5)
      clf
      plot(abs(foff_statemach_log(ne,:) - foff_hz < allowable_error));
    end

    % plots if single dimension vector

    if length(EbNovec) == 1
      fmin = -200; fmax = 200;
      figure(1)
      clf;
      subplot(411)
      plot(foff_log(ne,:))
      axis([1 frames fmin fmax]);
      ylabel("Foff raw")
      subplot(412)
      plot(foff_median_log(ne,:))
      axis([1 frames fmin fmax]);
      ylabel("Foff median")
      subplot(413)
      plot(foff_statemach_log(ne,:),'g')
      ylabel("Foff state")
      axis([1 frames fmin fmax]);
      subplot(414)
      plot(foff_thresh_log(ne,:))
      ylabel("Foff thresh")
      axis([1 frames fmin fmax]);
      xlabel("Frames")
      grid;

      figure(2)
      clf;
      plot(maxS1_log(ne,:));
      axis([1 frames 0 0.2]);
      xlabel("Frames")
      ylabel("max(abs(S1/S2))")
      grid;
      hold on;
      plot(maxS2_log(ne,:),'g');
      hold off;

      figure(3)
      [n m] = size(S1_log);
      if strcmp(plot_type,"mesh")
        mesh(-200+400*(0:m-1)/256,1:n,abs(S1_log(:,:)));
        xlabel('Freq (Hz)'); ylabel('Frame num'); zlabel("max(abs(S1))")
      else
        imagesc(1:n,-200+400*(0:(m-1))/m,abs(S1_log(:,:))');
        set(gca,'YDir','normal')
        ylabel('Freq (Hz)'); xlabel('Frame num');
        axis([1 n -200 200])
      end  

      figure(4)
      clf
      [n m] = size(spectrogram);
      if strcmp(plot_type,"mesh")
        mesh((4000/m)*(1:m),1:n,spectrogram);
        xlabel('Freq (Hz)'); ylabel('Frame num'); zlabel('Amplitude (dB)');
      else
        imagesc(1:n,(4000/m)*(1:m),spectrogram')
        set(gca,'YDir','normal')
        ylabel('Freq (Hz)'); xlabel('Frame num');
        axis([1 n 500 2500])
      end

      sim_out.spec = spectrogram;
      sim_out.tx_fdm_log = spectrogram;
    end
  end
end

% ---------------------------------------------------------------------
% Run Automated Tests
% ---------------------------------------------------------------------

more off;

function test1
  global M;
  global Rs;

  sim_in.test_name = "Test 1: range of Eb/No (SNRs) in AWGN channel";
  sim_in.EbNovec = [3:10 99];
  sim_in.delay = M/2;
  sim_in.frames = Rs*3;
  sim_in.foff_hz(1:sim_in.frames) = 50;
  sim_in.startup_delay = 0.5*Rs;
  sim_in.allowable_error = 5;
  sim_in.hf_sim = 0;
  sim_in.hf_delay_ms = 2;
  sim_in.delay = M/2;
  sim_in.plot_type = "waterfall";

  sim_out = freq_off_est_test(sim_in);

  figure(5)
  clf
  subplot(211)
  plot(sim_in.EbNovec, sim_out.foff_sd)
  hold on;
  plot(sim_in.EbNovec, sim_out.foff_sd,'+')
  hold off;
  xlabel("Eb/No (dB)")
  ylabel("Std Dev (Hz)")
  axis([(min(sim_in.EbNovec)-1) (max(sim_in.EbNovec)+1) -1 10]);

  subplot(212)
  plot(sim_out.SNRvec,sim_out.foff_sd)
  hold on;
  plot(sim_out.SNRvec,sim_out.foff_sd,'+')
  hold off;
  xlabel("SNR (dB)")
  ylabel("Std Dev (Hz)")
  axis([(min(sim_out.SNRvec)-1)  (max(sim_out.SNRvec)+1) -1 10]);
end


function test2
  sim_in.test_name = "Test 2: range of Eb/No (SNRs) in HF multipath channel"
  sim_in.EbNovec = 0:10;
  sim_in.delay = 2;
  sim_in.hf_sim = 1;
  sim_in.hf_delay_ms = 2;
  sim_in.frames = Rs*2;
  sim_in.foff_hz = 0;
  sim_in.startup_delay = Rs/2;
  sim_in.allowable_error = 5;

  sim_out = freq_off_est_test(sim_in);

  figure(5)
  clf
  subplot(211)
  plot(sim_in.EbNovec,sim_out.foff_sd)
  hold on;
  plot(sim_in.EbNovec,sim_out.foff_sd,'+')
  hold off;
  xlabel("Eb/No (dB)")
  ylabel("Std Dev")
  axis([(min(sim_in.EbNovec)-1) (max(sim_in.EbNovec)+1) -1 10]);
end

function test3
  global M;
  global Rs;

  sim_in.test_name = "Test 3: 30 Seconds in HF multipath channel at 0dB-ish SNR";
  sim_in.EbNovec = 13;
  sim_in.hf_sim = 0;
  sim_in.hf_delay_ms = 2;
  sim_in.delay = M/2;
  sim_in.frames = Rs;
  sim_in.foff_hz(1:sim_in.frames) = -50;
  sim_in.startup_delay = Rs; % allow 1 second in heavily faded channels      
  sim_in.allowable_error = 5;
  sim_in.plot_type = "mesh";
  sim_out = freq_off_est_test(sim_in);
endfunction

function animated_gif
  figure(4)
  for i=5:5:360
    view(i,45)
    filename=sprintf('fdmdv_fig%05d.png',i);
    print(filename);
  end
  if 0
  for i=90:-5:-270
    view(45,i)
    filename=sprintf('fdmdv_fig%05d.png',i);
    print(filename);
  end
  end
endfunction

test3;

