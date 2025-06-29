% ofdm_acquisition.m
% David Rowe Jan 2021
%
% Simulations used for development of HF data modem burst mode acquisition
%
% To run headless on a server:
%
%   DISPLAY=\"\" octave-cli --no-gui -qf ofdm_dev.m > 210218.txt &

ofdm_lib;
channel_lib;

% Build a vector of Tx bursts in noise, one burst occurs every padded_burst_len samples

function [rx tx_preamble tx_postamble burst_len padded_burst_len ct_targets states] = generate_bursts(sim_in)
  config = ofdm_init_mode(sim_in.mode);
  states = ofdm_init(config);
  ofdm_load_const;

  tx_preamble = states.tx_preamble; tx_postamble = states.tx_postamble;
   
  Nbursts = sim_in.Nbursts;
  tx_bits = create_ldpc_test_frame(states, coded_frame=0);
  tx_burst = [tx_preamble ofdm_mod(states, tx_bits) tx_postamble];
  burst_len = length(tx_burst);
  tx_burst = ofdm_hilbert_clipper(states, tx_burst, tx_clip_en=0);
  padded_burst_len = Fs+burst_len+Fs;
  
  tx = []; ct_targets = [];
  for f=1:Nbursts
    % 100ms of jitter in the burst start point
    jitter = floor(rand(1,1)*0.1*Fs);
    tx_burst_padded = [zeros(1,Fs+jitter) tx_burst zeros(1,Fs-jitter)];
    ct_targets = [ct_targets Fs+jitter];
    tx = [tx tx_burst_padded];
  end
  
  % adjust channel simulator SNR setpoint given (burst on length)/(sample length) ratio
  mark_space_SNR_offset = 10*log10(burst_len/padded_burst_len);
  SNRdB_setpoint = sim_in.SNR3kdB + mark_space_SNR_offset;
  %printf("SNR3kdB: %f Burst offset: %f\n", sim_in.SNR3kdB, mark_space_SNR_offset)
  rx = channel_simulate(Fs, SNRdB_setpoint, sim_in.foff_Hz, sim_in.channel, tx);
endfunction


function results = evaluate_candidate(states, det, i, Nsamperburstpadded, ct_target, foff_Hz, ttol_samples, ftol_hz)
  results.candidate = 0;
  if det.timing_mx > states.timing_mx_thresh
    % OK we have located a candidate peak
    
    % re-base ct_est to be wrt start of current burst reference frame
    ct_est = det.ct_est - (i-1)*Nsamperburstpadded;
        
    delta_ct = abs(ct_est-ct_target);
    delta_foff = det.foff_est-foff_Hz;
	
    ok = (abs(delta_ct) < ttol_samples) && (abs(delta_foff) < ftol_hz);
    
    results.candidate = 1; results.ct_est = ct_est; results.delta_ct = delta_ct; results.delta_foff = delta_foff; results.ok = ok;
  end    
endfunction


% test frame by frame acquisition algorithm

function Pa = frame_by_frame_acquisition_test(mode="datac1", Ntests=10, channel="awgn", SNR3kdB=100, foff_Hz=0, verbose_top=0) 
  sim_in.SNR3kdB = SNR3kdB;
  sim_in.channel = channel;
  sim_in.foff_Hz = foff_Hz;  
  sim_in.mode = mode;
  sim_in.Nbursts = Ntests;
  [rx tx_preamble tx_postamble Nsamperburst Nsamperburstpadded ct_targets states] = generate_bursts(sim_in);
  states.verbose = bitand(verbose_top,3);
  ofdm_load_const;
  
  timing_mx_log = []; ct_log = []; delta_ct_log = []; delta_foff_log = []; state_log = [];

  % allowable tolerance for acquistion
  ftol_hz = 2;              % we can sync up on this (todo: make mode selectable)
  ttol_samples = 0.006*Fs;  % CP length (todo: make mode selectable)
  target_acq = zeros(1,Ntests);
  
  state = 'acquisition';
  
  for n=1:Nsamperframe:length(rx)-2*Nsamperframe
     pre = burst_acquisition_detector(states, rx, n, tx_preamble);   
     post = burst_acquisition_detector(states, rx, n, tx_postamble);
     
     % adjust time reference for this simulation
     pre.ct_est += n;
     post.ct_est += n;

     timing_mx_log = [timing_mx_log [pre.timing_mx; post.timing_mx]];
     
     % state machine to simulate acquisition/demod processing
     
     next_state = state;
     if strcmp(state,'acquisition')
       state_log = [state_log 0];

       % work out what burst we are evaluating
       i = ceil(n/Nsamperburstpadded); % i-th burst we are evaluating
       w = (i-1)*Nsamperburstpadded;   % offset of burst in s() for plotting purposes
       ct_target_pre = ct_targets(i);
       ct_target_post = ct_targets(i) + Nsamperburst - length(tx_preamble);

       pre_eval = evaluate_candidate(states, pre, i, Nsamperburstpadded, ct_target_pre, foff_Hz, ttol_samples, ftol_hz);
       post_eval = evaluate_candidate(states, post, i, Nsamperburstpadded, ct_target_post, foff_Hz, ttol_samples, ftol_hz);
     
       if pre_eval.candidate
         if pre_eval.ok == 0
           target_acq(i) = -1; % flag bad candidate
         end
         if pre_eval.ok && (target_acq(i) == 0)
           target_acq(i) = 1;  % flag a sucessful acquisition
           next_state = "demod";
           modem_frame = 0;
         end
         delta_ct_log = [delta_ct_log pre_eval.delta_ct];
         delta_foff_log = [delta_foff_log pre_eval.delta_foff];
         ct_log = [ct_log w+pre_eval.ct_est];
         if states.verbose
           printf("Pre  i: %2d n: %8d ct_est: %6d delta_ct: %6d foff_est: %5.1f timing_mx: %3.2f Acq: %d\n",
                  i, n, pre_eval.ct_est, pre_eval.delta_ct, pre.foff_est, pre.timing_mx, target_acq(i));
         end
       end
         
       if post_eval.candidate
         if post_eval.ok == 0
           target_acq(i) = -1; % flag bad candidate
         end
         if post_eval.ok && (target_acq(i) == 0)
           target_acq(i) = 1;  % flag a successful acquisition 
           next_state = "demod";
           modem_frame = Np-2;                               
         end
         delta_ct_log = [delta_ct_log post_eval.delta_ct];
         delta_foff_log = [delta_foff_log post_eval.delta_foff];
         ct_log = [ct_log w+post_eval.ct_est];
         if states.verbose
           printf("Post i: %2d n: %8d ct_est: %6d delta_ct: %6d foff_est: %5.1f timing_mx: %3.2f Acq: %d\n",
                  i, n, post_eval.ct_est, post_eval.delta_ct, post.foff_est, post.timing_mx, target_acq(i));
         end
       end
     end
    
     if strcmp(state, "demod")
        state_log = [state_log 1];
        modem_frame++;
        if modem_frame > states.Np
          next_state = "acquisition";
        end
     end
     
     state = next_state;         
  end
  
  if bitand(verbose_top,8)
    figure(1); clf; 
               plot(timing_mx_log(1,:),'+-;preamble;'); 
               hold on; 
               plot(timing_mx_log(2,:),'o-;postamble;'); 
               plot(0.35+0.1*state_log,'-g;state;'); 
               title('mx log'); axis([0 length(timing_mx_log) 0 0.5]); grid;
               hold off;
    figure(4); clf; plot(real(rx)); axis([0 length(rx) -3E4 3E4]);
               hold on;
               plot(ct_log,zeros(1,length(ct_log)),'r+','markersize', 25, 'linewidth', 2);
               hold off; 
    figure(5); clf; plot_specgram(rx, Fs, 500, 2500);
  end
  
  Pa = length(find(target_acq == 1))/Ntests;
  printf("%s %s SNR: %3.1f foff: %3.1f P(acq) = %3.2f\n", mode, channel, SNR3kdB, foff_Hz, Pa);
endfunction


% test frame by frame across modes, channels, and SNR (don't worry about sweeping freq)

function acquistion_curves_frame_by_frame_modes_channels_snr(Ntests=5, quick_test=0)
  modes={'datac0', 'datac1', 'datac3'};
  if quick_test
    Ntests = 5;
    channels={'awgn','mpp'}; SNR = [0 5];
  else
    channels={'awgn', 'mpm', 'mpp', 'notch'};
    SNR = [ -10 -5 -3.5 -1.5 0 1.5 3.5 5 7.5 10 15];
  end
  
  cc = ['b' 'g' 'k' 'c' 'm' 'r'];
  pt = ['+' '*' 'x' 'o' '+' '*'];
 
  for i=1:length(modes)
    figure(i); clf; hold on; title(sprintf("%s P(acquisition)", modes{i}));
  end
  
  for m=1:length(modes)
    figure(m);
    for c=1:length(channels)
      Pa_log = [];
      for s=1:length(SNR)
        Pa = frame_by_frame_acquisition_test(modes{m}, Ntests, channels{c}, SNR(s), foff_hz=0, verbose=1);
        Pa_log = [Pa_log Pa];
      end
      l = sprintf('%c%c-;%s;', cc(c), pt(c), channels{c}); 
      plot(SNR, Pa_log, l, 'markersize', 10);
    end
  end
  
  for i=1:length(modes)
    figure(i); grid;
    xlabel('SNR3k dB'); legend('location', 'southeast'); 
    xlim([min(SNR)-2 max(SNR)+2]); ylim([0 1.1]);
    print('-dpng', sprintf("%s_ofdm_dev_acq_curves_fbf_%s.png", datestr(clock(),"yyyy-mm-dd"), modes{i}));
  end 
endfunction

% main starts here -----------------------------------------

format;
more off;
pkg load signal;
graphics_toolkit ("gnuplot");
randn('seed',1);

% ---------------------------------------------------------
% choose simulation to run here 
% ---------------------------------------------------------

frame_by_frame_acquisition_test("datac0", Ntests=5, 'mpp', SNR3kdB=5, foff_hz=0, verbose=1+8);
%acquistion_curves_frame_by_frame_modes_channels_snr(Ntests=50, quick_test=0)
