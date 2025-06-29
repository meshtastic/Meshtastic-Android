% ofdm_rx.m
% David Rowe May 2018
%
% OFDM file based uncoded rx to unit test core OFDM modem.  See also
% ofdm_ldpc_rx which includes LDPC and interleaving, and ofdm_demod.c

#{
    1. Streaming mode operation:
  
       ofdm_rx("test_datac0.raw","datac0")
    
    2. Burst mode, tell state machine there is one packet in each burst:
    
       ofdm_rx("test_datac0.raw","datac0","packetsperburst",1)
       
    3. Burst mode, enable only postamble detecion:
    
       ofdm_rx("test_datac0.raw","datac0","packetsperburst",1, "postambletest")
#}

function ofdm_rx(filename, mode="700D", varargin)
  ofdm_lib;
  more off;
  pkg load signal;
  
  % init modem

  config = ofdm_init_mode(mode);
  states = ofdm_init(config);
  print_config(states);
  ofdm_load_const;
  states.verbose = 0;
  pass_ber = 0;
  pass_packet_count = 0;
   
  i = 1;
  while i <= length(varargin)
    if strcmp(varargin{i},"packetsperburst")
      states.data_mode = "burst"; % use pre/post amble based sync
      states.packetsperburst = varargin{i+1}; i++;
      states.postambledetectoren = 1;
    elseif strcmp(varargin{i},"passber")
      pass_ber = varargin{i+1}; i++;
    elseif strcmp(varargin{i},"passpacketcount")
      pass_packet_count = varargin{i+1}; i++;
    elseif strcmp(varargin{i},"postambletest")
      printf("postamble test!\n");
      states.postambletest = 1;
      % at high SNR avoid firing on data frames just before postamble
      states.timing_mx_thresh = 0.15;
    else
      printf("\nERROR unknown argument: %s\n", varargin{i});
      return;
    end
    i++; 
  end
  
  % load real samples from file

  Ascale = states.amp_scale/2; % as input is a real valued signal
  frx=fopen(filename,"rb"); rx = fread(frx, Inf, "short")/Ascale; fclose(frx);
  Nsam = length(rx);  prx = 1;

  % OK re-generate tx frame for BER calcs

  tx_bits = create_ldpc_test_frame(states, coded_frame=0);

  % init logs and BER stats

  rx_np_log = []; timing_est_log = []; delta_t_log = []; foff_est_hz_log = [];
  sample_point_log = [];
  channel_est_pilot_log = []; snr_log = [];
  Terrs = Tbits = Terrs_coded = Tbits_coded = Tpackets = Tpacketerrs = 0;
  packet_count = frame_count = 0;
  Nerrs_coded_log = Nerrs_log = [];
  error_positions = [];

  prx = 1;
  nin = Nsamperframe+2*(M+Ncp);
  states.verbose = 1;
  Nsymsperpacket = Nbitsperpacket/bps; Nsymsperframe = Nbitsperframe/bps;
  rx_syms = zeros(1,Nsymsperpacket); rx_amps = zeros(1,Nsymsperpacket);
  Nerrs = 0; rx_uw = zeros(1,states.Nuwbits);

  % main loop ----------------------------------------------------------------
  
  f = 1;
  while(prx < Nsam)
    
    % insert samples at end of buffer, set to zero if no samples
    % available to disable phase estimation on future pilots on last
    % frame of simulation

    lnew = min(Nsam-prx,states.nin);
    rxbuf_in = zeros(1,states.nin);

    if lnew
      rxbuf_in(1:lnew) = rx(prx:prx+lnew-1);
    end
    prx += states.nin;

    if states.verbose
      printf("f: %3d nin: %4d st: %-6s ", f, states.nin, states.sync_state);
    end

    if strcmp(states.sync_state,'search')
      [timing_valid states] = ofdm_sync_search(states, rxbuf_in);
    else
      % accumulate a buffer of data symbols for this packet
      rx_syms(1:end-Nsymsperframe) = rx_syms(Nsymsperframe+1:end);
      rx_amps(1:end-Nsymsperframe) = rx_amps(Nsymsperframe+1:end);
      [states rx_bits achannel_est_pilot_log arx_np arx_amp] = ofdm_demod(states, rxbuf_in);
      rx_syms(end-Nsymsperframe+1:end) = arx_np;
      rx_amps(end-Nsymsperframe+1:end) = arx_amp;

      rx_uw = extract_uw(states, rx_syms(end-Nuwframes*Nsymsperframe+1:end), rx_amps(end-Nuwframes*Nsymsperframe+1:end));

      % We need the full packet of symbols before disassembling and checking for bit errors
      if states.modem_frame == (states.Np-1);
        rx_bits = zeros(1,Nbitsperpacket);
        for s=1:Nsymsperpacket
          if bps == 2
             rx_bits(bps*(s-1)+1:bps*s) = qpsk_demod(rx_syms(s));
          elseif bps == 4
             rx_bits(bps*(s-1)+1:bps*s) = qam16_demod(states.qam16,rx_syms(s), rx_amps(s));
          end
        end

        errors = xor(tx_bits, rx_bits);
        Nerrs = sum(errors);
        Nerrs_log = [Nerrs_log Nerrs];
        Terrs += Nerrs;
        Tbits += Nbitsperpacket;
        packet_count++;
        
        % per-packet SNR estimate
        EsNo_estdB = esno_est_calc(rx_syms);
        SNR_estdB = snr_from_esno(states, EsNo_estdB);
        snr_log = [snr_log SNR_estdB];
      end

      % we are in sync so log states

      rx_np_log = [rx_np_log arx_np];
      timing_est_log = [timing_est_log states.timing_est];
      sample_point_log = [sample_point_log states.sample_point];
      delta_t_log = [delta_t_log states.delta_t];
      foff_est_hz_log = [foff_est_hz_log states.foff_est_hz];
      channel_est_pilot_log = [channel_est_pilot_log; achannel_est_pilot_log];

      frame_count++;
    end
    
    states = sync_state_machine(states, rx_uw);

    if states.verbose
      if strcmp(states.last_sync_state,'search') == 0
        if (states.modem_frame == 0) && (strcmp(states.sync_state, "trial") == 0)
          printf(" euw: %3d %d mf: %2d pbw: %s foff: %4.1f eraw: %3d snr: %5.2f",
                 states.uw_errors, states.sync_counter, states.modem_frame, states.phase_est_bandwidth(1),
                 states.foff_est_hz, Nerrs, SNR_estdB);
        else
          printf(" euw: %3d %d mf: %2d pbw: %s foff: %4.1f",
                 states.uw_errors, states.sync_counter, states.modem_frame, states.phase_est_bandwidth(1),
                 states.foff_est_hz);
        end
      end
      printf("\n");
    end

    % reset stats if in streaming mode, don't reset if in burst mode
    if strcmp(states.data_mode, "streaming") && states.sync_start
      Nerrs_log = [];
      Terrs = Tbits = frame_count = 0;
      rx_np_log = [];
      sig_var_log = []; noise_var_log = [];
    end
    
    f++;
  end
  Nframes = f;

  ber = Terrs/(Tbits+1E-12);
  printf("\nBER..: %5.4f Tbits: %5d Terrs: %5d\n", ber, Tbits, Terrs);

  % If we have enough frames, calc BER discarding first few frames where freq
  % offset is adjusting

  Ndiscard = 20;
  if packet_count > Ndiscard
    Terrs -= sum(Nerrs_log(1:Ndiscard)); Tbits -= Ndiscard*Nbitsperframe;
    printf("BER2.: %5.4f Tbits: %5d Terrs: %5d\n", Terrs/Tbits, Tbits, Terrs);
  end

  SNR_estdB = mean(snr_log);
  printf("Packets: %3d Npre: %d Npost: %d SNR3k: %3.2f\n",
         packet_count, states.npre, states.npost, SNR_estdB);

  figure(1); clf;
  tmp = exp(j*pi/4)*rx_np_log(floor(end/4):floor(end-end/8));
  plot(tmp,'+');
  mx = 2*max(abs(tmp));
  axis([-mx mx -mx mx]);
  title('Scatter');

  figure(2); clf;
  plot(angle(channel_est_pilot_log(:,2:Nc)),'g+', 'markersize', 5);
  title('Phase est');
  axis([1 length(channel_est_pilot_log) -pi pi]);

  figure(3); clf;
  plot(abs(channel_est_pilot_log(:,:)),'g+', 'markersize', 5);
  title('Amp est');
  axis([1 length(channel_est_pilot_log) -3 3]);

  figure(4); clf;
  subplot(211)
  stem(delta_t_log)
  title('delta t');
  subplot(212)
  plot(timing_est_log,';timing est;');
  hold on; plot(sample_point_log,';sample point;'); hold off;

  figure(5); clf;
  plot(foff_est_hz_log)
  mx = max(abs(foff_est_hz_log))+1;
  axis([1 max(Nframes,2) -mx mx]);
  title('Fine Freq');
  ylabel('Hz')

  figure(6); clf;
  stem(Nerrs_log);
  title('Errors/modem frame')
  if length(Nerrs_log) > 1
      axis([1 length(Nerrs_log) 0 Nbitsperframe*rate/2]);
  endif

  figure(7); clf;
  plot(snr_log);
  title('SNR estimates');

  figure(8); clf; plot_specgram(rx, 8000, 500, 2500);

  % optional pass criteria for ctests
  if pass_ber > 0
    if packet_count && (ber < pass_ber) printf("Pass!\n"); else printf("Fail!\n"); end;
  end
  if pass_packet_count > 0
    if packet_count >= pass_packet_count printf("Pass!\n"); else printf("Fail!\n"); end;
  end
endfunction
