% ofdm_lib.m
% David Rowe Mar 2017

#{
  Library of functions that implement a PSK OFDM modem.
#}

1;
qam16;
esno_est;

%-------------------------------------------------------------
% ofdm_init
%-------------------------------------------------------------

#{

  Modem frame has a pilot every Ns symbols. There are Ns-1 data
  symbols between every pilot.

   e.g. for Ns=4, Nc=6:

    |-Nc-|          Time
    DDDDDD           |
   PPPPPPPP  ---     |
    DDDDDD    |      |
    DDDDDD    Ns     |
    DDDDDD    |      |
   PPPPPPPP  ---    \|/
    DDDDDD    |      |

   Freq------------------>

   In this figure, time flows down, freq across.
#}

function states = ofdm_init(config)
  Rs = config.Rs; 
  Tcp = config.Tcp; 
  Ns = config.Ns; 
  Nc = config.Nc;
  bps = config.bps;
  Np = config.Np;
  Ntxtbits = config.Ntxtbits;
  Nuwbits = config.Nuwbits;
  ftwindow_width = config.ftwindow_width;
  timing_mx_thresh = config.timing_mx_thresh;
  tx_uw = config.tx_uw;
  bad_uw_errors = config.bad_uw_errors;
  amp_scale = config.amp_scale;
  amp_est_mode = config.amp_est_mode;
  EsNo_est_all_symbols = config.EsNo_est_all_symbols;
  EsNodB = config.EsNodB; 
  state_machine = config.state_machine; 
  edge_pilots = config.edge_pilots;
  clip_gain1 = config.clip_gain1;
  clip_gain2 = config.clip_gain2;
  foff_limiter = config.foff_limiter; 
  txbpf_width_Hz = config.txbpf_width_Hz;
  data_mode = config.data_mode;
       
  states.Fs = 8000;
  states.bps = bps;
  states.Rs = Rs;
  states.Tcp = Tcp;
  states.Ns = Ns;                                 % one pilot every Ns symbols, e.g. Ns=4, ...PDDDPDDDP...
  states.Nc = Nc;                                 % Number of carriers
  states.M  = states.Fs/Rs;                       % oversampling rate
  states.Ncp = Tcp*states.Fs;
  states.Nbitsperframe = (Ns-1)*Nc*bps;           % total bits in all data symbols in modem frame
  states.Nsampersymbol = states.M+states.Ncp;     % number of samples in a single symbol
  states.Nsamperframe  = Ns*states.Nsampersymbol; % number of samples in a modem frame
  states.qam16 = [
    1 + j,  1 + j*3,  3 + j,  3 + j*3;
    1 - j,  1 - j*3,  3 - j,  3 - j*3;
   -1 + j, -1 + j*3, -3 + j, -3 + j*3;
   -1 - j, -1 - j*3, -3 - j, -3 - j*3]/3;
  rms = sqrt(states.qam16(:)'*states.qam16(:)/16);% set average Es to 1
  states.qam16 /= rms;
  states.qam16 *= exp(-j*pi/4);                   % same rotation as QPSK constellation
  states.Np = Np;                                 % number of modem frames per packet. In some modes we want
                                                  % the total packet of data to span multiple modem frames, e.g. HF data
                                                  % and/or when the FEC codeword is larger than the one
                                                  % modem frame.  In other modes (e.g. 700D/2020) Np=1, ie the modem frame
                                                  % is the same length as the packet/FEC codeword.
  states.Nbitsperpacket = Np*states.Nbitsperframe;
  states.Tpacket = Np*Ns*(Tcp+1/Rs);              % time for one packet in ms

  states.Ntxtbits = Ntxtbits;                     % reserved bits/frame for auxillary text information.  Uncoded/unprotected so may
                                                  % be of limited use going forward, consider setting to 0
  states.Nuwbits  = Nuwbits;

  % some basic sanity checks
  assert(floor(states.M) == states.M);

  % UW symbol placement. 
  % Note we need to fill each UW symbols with bits.  The LDPC decoder
  % works on symbols so we can't break up any symbols into UW/FEC
  % encoded bits.

  states.uw_ind = states.uw_ind_sym = [];
  uw_step = Nc+1;                                % default step for UW sym placement

  % lets see if all UW syms will fit in frame
  Nuwsyms = states.Nuwbits/bps;
  Ndatasymsperframe = (Ns-1)*Nc;
  last_sym = floor(Nuwsyms*uw_step/bps+1);
  if last_sym > states.Np*Ndatasymsperframe
    uw_step = Nc-1;                                 % try a different step
  end
  last_sym = floor(Nuwsyms*uw_step/bps+1);
  assert(last_sym <= states.Np*Ndatasymsperframe);  % we still can't fit them all

  % Place UW symbols in frame
  for i=1:Nuwsyms
    ind_sym = floor(i*uw_step/bps+1);
    % printf("%d sym: %d\n",i, ind_sym);
    states.uw_ind_sym = [states.uw_ind_sym ind_sym];   % symbol index
    for b=bps-1:-1:0
      states.uw_ind = [states.uw_ind bps*ind_sym-b];   % bit index
    end
  end

  % how many of the first few frames have UW symbols in them
  Nsymsperframe = states.Nbitsperframe/states.bps;
  states.Nuwframes = ceil(states.uw_ind_sym(end)/Nsymsperframe);

  states.tx_uw = tx_uw;
  assert(length(states.tx_uw) == states.Nuwbits);
  tx_uw_syms = [];
  for b=1:bps:states.Nuwbits
    if bps == 2 tx_uw_syms = [tx_uw_syms qpsk_mod(states.tx_uw(b:b+1))]; end
    if bps == 4 tx_uw_syms = [tx_uw_syms qam16_mod(states.qam16, states.tx_uw(b:b+bps-1))]; end
  end
  states.tx_uw_syms = tx_uw_syms;
  % if the UW has this many errors it is "bad", the binomal cdf can be used to set this:
  %   Nuw=12; plot(0:Nuw, binocdf(0:Nuw,Nuw,0.05)); hold on; plot(binocdf(0:Nuw,Nuw,0.5)); hold off;
  states.bad_uw_errors = bad_uw_errors;

  states.ofdm_peak = 16384;
  % use this to scale tx output to 16 bit short to a peak value of 16384.  Adjusted by experiment
  states.amp_scale = amp_scale;
  % when using the clipping, this is the manual gain value.  Adjusted by experiment, trade off between
  % increased average power and BER
  states.clip_gain1 = clip_gain1;
  states.clip_gain2 = clip_gain2;
  states.txbpf_width_Hz = txbpf_width_Hz;

  % this is used to scale inputs to LDPC decoder to make it amplitude indep
  states.mean_amp = 0;

  % use a fixed EsNo for LDPC decoder, this seems to work OK and avoid another estimator
  states.EsNodB = EsNodB;

  % generate same BPSK pilots each time
  rand('seed',1);
  states.pilots = 1 - 2*(rand(1,Nc+2) > 0.5);
  %printf("number of pilots total: %d\n", length(states.pilots));

  % If set, place pilots at carrier 1 and Nc+2 to support low bandwith phase est over grid
  % of 12 pilot_samples.  Used for 700D and 2020
  states.edge_pilots = edge_pilots;
  if states.edge_pilots == 0
     states.pilots(1) = 0;
     states.pilots(Nc+2) = 0;
  end
  
  % carrier tables for up and down conversion
  states.fcentre = fcentre = 1500;
  alower = fcentre - Rs * (Nc/2);  % approx frequency of lowest carrier
  Nlower = round(alower / Rs) - 1; % round this to nearest integer multiple from 0Hz to keep DFT happy
  %printf("  fcentre: %f alower: %f alower/Rs: %f Nlower: %d\n", fcentre, alower, alower/Rs, Nlower);
  w = (Nlower:Nlower+Nc+1)*2*pi/(states.Fs/Rs);
  W = zeros(Nc+2,states.M);
  for c=1:Nc+2
    W(c,:) = exp(j*w(c)*(0:states.M-1));
  end
  states.w = w;
  states.W = W;

  % fine timing search +/- window_width/2 from current timing instant,
  % set this to roughly twice the maximum delay spread
  states.ftwindow_width = ftwindow_width;

  % magic number we adjust by experiment (see ofdm_dev.m acquisition tests, blog post on 700D sync)
  states.timing_mx_thresh = timing_mx_thresh;

  % Receive buffer: rxbufst + D P DDD P DDD P DDD P D
  %                                   ^
  %                                   nominal start of current modem frame
  
  if length(data_mode)
    Nrxbufhistory = (states.Np+2)*states.Nsamperframe;     % extra storage at start of rxbuf to allow us to step back in time 
  else
    Nrxbufhistory = 0;
  end
  states.rxbufst = Nrxbufhistory;                          % start of rxbuf window used for demod of current rx frame
  states.Nrxbufhistory = Nrxbufhistory;
  
  %                       D                    P DDD P DDD P DDD             P                    D
  states.Nrxbufmin = states.Nsampersymbol + 3*states.Nsamperframe + states.Nsampersymbol + states.Nsampersymbol;
  states.Nrxbuf = Nrxbufhistory + states.Nrxbufmin;
  states.rxbuf = zeros(1, states.Nrxbuf);

  % default settings on a bunch of options and states

  states.verbose = 0;
  states.timing_en = 1;
  states.foff_est_en = 1;
  states.phase_est_en = 1;
  states.phase_est_bandwidth = "high";
  states.dpsk = 0;
  states.amp_est_mode = amp_est_mode;

  states.foff_est_gain = 0.1;
  states.foff_limiter = foff_limiter;
  states.foff_est_hz = 0;
  states.sample_point = states.timing_est = 1;
  states.nin = states.Nsamperframe;
  states.timing_valid = 0;
  states.timing_mx = 0;
  states.coarse_foff_est_hz = 0;

  states.foff_metric = 0;

  % generate OFDM pilot symbol, used for timing and freq offset est

  rate_fs_pilot_samples = states.pilots * W/states.M;

  % During tuning it was found that not including the cyc prefix in
  % rate_fs_pilot_samples produced better fest results

  %states.rate_fs_pilot_samples = [rate_fs_pilot_samples(states.M-states.Ncp+1:states.M) rate_fs_pilot_samples];
  states.rate_fs_pilot_samples = [zeros(1,states.Ncp) rate_fs_pilot_samples];

  % pre-compute a constant used to detect valid modem frames

  Npsam = length(states.rate_fs_pilot_samples);
  states.timing_norm = Npsam*(states.rate_fs_pilot_samples * states.rate_fs_pilot_samples');
  % printf("timing_norm: %f\n", states.timing_norm)

  % sync state machine

  states.sync_state = states.last_sync_state = 'search';
  states.uw_errors = 0;
  states.sync_counter = 0;
  states.frame_count = 0;                                 % number of frames we have been in sync
  states.sync_start = 0;
  states.sync_end = 0;
  states.modem_frame = 0;                                 % keep track of how many frames received in packet
  states.state_machine = state_machine;                   % mode specific state machine
  states.packetsperburst = 0;                             % for OFDM data modes, how many packets before we reset state machine
  states.postambledetectoren = strcmp(data_mode,"burst");
  states.npre = states.npost = 0;                         % counters for logging
  
  % LDPC code is optionally enabled

  states.rate = 1.0;
  states.ldpc_en = 0;

  % init some output states for logging

  states.rx_sym = zeros(1+Ns+1+1, Nc+2);

  % Es/No (SNR) est states

  states.EsNo_est_all_symbols = EsNo_est_all_symbols;
  states.clock_offset_est = 0;

  % pre-amble for data modes
  states.data_mode = data_mode;
  if length(states.data_mode)
    states.tx_preamble = ofdm_generate_preamble(states, 2);
    states.tx_postamble = ofdm_generate_preamble(states, 3);
  end
  
  % automated tests
  test_qam16_mod_demod(states.qam16);
  test_assemble_disassemble(states);
endfunction


%------------------------------------------------------------------------------
% ofdm_init_mode - Helper function to set up modems for various FreeDV modes,
%                  and parse mode string.
%------------------------------------------------------------------------------

function config = ofdm_init_mode(mode="700D")
  % defaults for 700D
  
  Tcp = 0.002; 
  Ns = 8;
  Ts = 0.018; 
  Nc = 17;
  config.bps = 2; 
  config.Np = 1;
  config.Ntxtbits = 4;
  config.Nuwbits = 5*config.bps;
  config.ftwindow_width = 32;
  config.timing_mx_thresh  = 0.35;
  config.bad_uw_errors = 3;
  config.amp_scale = 245E3;
  config.amp_est_mode = 0;
  config.EsNo_est_all_symbols = 1;
  config.EsNodB = 3;
  config.state_machine = "voice1";
  config.edge_pilots = 1;
  config.clip_gain1 = 2.5;
  config.clip_gain2 = 0.8;
  config.foff_limiter = 0;
  config.txbpf_width_Hz = 2000;
  config.data_mode = "";

  if strcmp(mode,"700D") ||  strcmp(mode,"700d")
    % defaults above
  elseif strcmp(mode,"700E") ||  strcmp(mode,"700e")
    Ts = 0.014; Tcp=0.006; Nc = 21; Ns=4;
    config.edge_pilots = 0; config.state_machine = "voice2";
    config.Nuwbits = 12; config.bad_uw_errors = 3; config.Ntxtbits = 2;
    config.amp_est_mode = 1; config.ftwindow_width = 80;
    config.amp_scale = 155E3; config.clip_gain1 = 3; config.clip_gain2 = 0.8;
    config.foff_limiter = 1;
  elseif strcmp(mode,"2020")
    Ts = 0.0205; Nc = 31;
    config.amp_scale = 167E3; config.clip_gain1 = 2.5; config.clip_gain2 = 0.8;
  elseif strcmp(mode,"2020B")
    Ts = 0.014; Tcp = 0.004; Nc = 29; Ns=5;
    config.Ntxtbits = 4; config.Nuwbits = 8*2; config.bad_uw_errors = 5;
    config.amp_scale = 130E3; config.clip_gain1 = 2.5; config.clip_gain2 = 0.8;
    config.edge_pilots = 0; config.state_machine = "voice2";
    config.foff_limiter = 1; config.ftwindow_width = 64;
    config.txbpf_width_Hz = 2200;
  elseif strcmp(mode,"qam16c1")
    Ns=5; config.Np=5; Tcp = 0.004; Ts = 0.016; Nc = 33; config.data_mode = "streaming";
    config.bps=4; config.Ntxtbits = 0; config.Nuwbits = 15*4; config.bad_uw_errors = 5;
    config.state_machine = "data";
    config.ftwindow_width = 32; config.amp_scale = 132E3;
    config.EsNo_est_all_symbols = 0; config.amp_est_mode = 1; config.EsNodB = 10;
  elseif strcmp(mode,"qam16c2")
    Ns=5; config.Np=31; Tcp = 0.004; Ts = 0.016; Nc = 33; config.data_mode = "streaming";
    config.bps=4; config.Ntxtbits = 0; config.Nuwbits = 42*4; config.bad_uw_errors = 15;
    config.ftwindow_width = 80; config.amp_scale = 135E3; config.state_machine = "data";
    config.EsNo_est_all_symbols = 0; config.amp_est_mode = 1; config.EsNodB = 10;
    config.tx_uw = zeros(1,config.Nuwbits = 42*4);
    config.tx_uw(1:24) = [1 1 0 0  1 0 1 0  1 1 1 1  0 0 0 0  1 1 1 1  0 0 0 0];
    config.tx_uw(end-24+1:end) = [1 1 0 0  1 0 1 0  1 1 1 1  0 0 0 0  1 1 1 1  0 0 0 0];
  elseif strcmp(mode,"datac0")
    Ns=5; config.Np=4; Tcp = 0.006; Ts = 0.016; Nc = 9; config.data_mode = "streaming";
    config.Ntxtbits = 0; config.Nuwbits = 32; config.bad_uw_errors = 9;
    config.state_machine = "data";
    config.ftwindow_width = 80; config.amp_est_mode = 1; config.EsNodB = 3;
    config.edge_pilots = 0; config.timing_mx_thresh = 0.08;
    config.tx_uw = zeros(1,config.Nuwbits);
    config.tx_uw(1:16) = [1 1 0 0  1 0 1 0  1 1 1 1  0 0 0 0];
    config.amp_scale = 300E3; config.clip_gain1 = 2.2; config.clip_gain2 = 0.8;
  elseif strcmp(mode,"datac1")
    Ns=5; config.Np=38; Tcp = 0.006; Ts = 0.016; Nc = 27; config.data_mode = "streaming";
    config.Ntxtbits = 0; config.Nuwbits = 16; config.bad_uw_errors = 6;
    config.state_machine = "data";
    config.ftwindow_width = 80; config.amp_est_mode = 1; config.EsNodB = 3;
    % just use default clipper (with no BPF) and let SSB BPF clean it up
    % config.amp_scale = 125E3; config.clip_gain1 = 2.5; config.clip_gain2 = 0.8;
    config.edge_pilots = 0; config.timing_mx_thresh = 0.10;
    config.tx_uw = [1 1 0 0  1 0 1 0  1 1 1 1  0 0 0 0];
  elseif strcmp(mode,"datac3")
    Ns=5; config.Np=29; Tcp = 0.006; Ts = 0.016; Nc = 9; config.data_mode = "streaming";
    config.edge_pilots = 0;
    config.Ntxtbits = 0; config.Nuwbits = 40; config.bad_uw_errors = 10;
    config.ftwindow_width = 80; config.timing_mx_thresh = 0.10;
    config.tx_uw = zeros(1,config.Nuwbits);
    config.tx_uw(1:24) = [1 1 0 0  1 0 1 0  1 1 1 1  0 0 0 0  1 1 1 1  0 0 0 0];
    config.tx_uw(end-24+1:end) = [1 1 0 0  1 0 1 0  1 1 1 1  0 0 0 0  1 1 1 1  0 0 0 0];
    config.amp_est_mode = 1; config.EsNodB = 3;
    config.state_machine = "data"; 
    config.amp_scale = 300E3; config.clip_gain1 = 2.2; config.clip_gain2 = 0.8;
  elseif strcmp(mode,"1")
    Ns=5; config.Np=10; Tcp=0; Tframe = 0.1; Ts = Tframe/Ns; Nc = 1;
  else
    % try to parse mode string for user defined mode
    vec = sscanf(mode, "Ts=%f Nc=%d Ncp=%f");
    Ts=vec(1); Nc=vec(2); Ncp=vec(3);
  end
  Rs=1/Ts;
  config.Rs = Rs; config.Tcp = Tcp; config.Ns = Ns; config.Nc = Nc;
  if !isfield(config,"tx_uw") 
    config.tx_uw = zeros(1,config.Nuwbits); 
  end  
end


%------------------------------------------------------------------------------
% print_config - utility function to use ascsii-art to describe the modem frame
%------------------------------------------------------------------------------

function print_config(states)
  ofdm_load_const;

  % ASCII-art packet visualisation
  s=1; u=1; Nuwsyms=length(uw_ind_sym);
  cr = 1:Nc+2;
  for f=1:Np
    for r=1:Ns
      for c=cr
        if r == 1
          sym="P";
        elseif c>1 && c <=(Nc+1)
          sym=".";
          if (u <= Nuwsyms) && (s == uw_ind_sym(u)) sym="U"; u++; end
          s++;
        else
          sym=" ";
        end
        printf("%s",sym);
      end
      printf("\n");
    end
  end

  printf("Nc=%d Ts=%4.3f Tcp=%4.3f Ns: %d Np: %d\n", Nc, 1/Rs, Tcp, Ns, Np);
  printf("Nsymperframe: %d Nbitsperpacket: %d Nsamperframe: %d Ntxtbits: %d Nuwbits: %d Nuwframes: %d\n",
          Ns*Nc, Nbitsperpacket, Nsamperframe, Ntxtbits, Nuwbits, Nuwframes);
  printf("uncoded bits/s: %4.1f\n",  Nbitsperpacket*Fs/(Np*Nsamperframe));
end

% Gray coded QPSK modulation function
function symbol = qpsk_mod(two_bits)
    two_bits_decimal = sum(two_bits .* [2 1]);
    switch(two_bits_decimal)
        case (0) symbol =  1;
        case (1) symbol =  j;
        case (2) symbol = -j;
        case (3) symbol = -1;
    endswitch
endfunction

% Gray coded QPSK demodulation function
function two_bits = qpsk_demod(symbol)
    bit0 = real(symbol*exp(j*pi/4)) < 0;
    bit1 = imag(symbol*exp(j*pi/4)) < 0;
    two_bits = [bit1 bit0];
endfunction


function out = freq_shift(in, foff, Fs)
  foff_rect = exp(j*2*pi*foff/Fs);
  foff_phase_rect = exp(j*0);

  for r=1:length(in)
    foff_phase_rect *= foff_rect;
    out(r) = in(r)*foff_phase_rect;
  end
endfunction


% -----------------------------------------------------------------
% ofdm_mod - modulates a complete packet (one or more modem frames)
% ----------------------------------------------------------------

function tx = ofdm_mod(states, tx_bits)
  ofdm_load_const;
  assert(length(tx_bits) == Nbitsperpacket);

  % map to symbols in linear array

  if bps == 1
    tx_sym_lin = 2*tx_bits - 1;
  end
  if bps == 2
    for s=1:Nbitsperpacket/bps
      tx_sym_lin(s) = qpsk_mod(tx_bits(2*(s-1)+1:2*s));
    end
  end
  if bps == 4
    for s=1:Nbitsperpacket/bps
      tx_sym_lin(s) = qam16_mod(states.qam16,tx_bits(4*(s-1)+1:4*s));
    end
  end

  tx = ofdm_txframe(states, tx_sym_lin);
endfunction


% ----------------------------------------------
% ofdm_txframe - modulates one packet of symbols
% ----------------------------------------------

function tx = ofdm_txframe(states, tx_sym_lin)
  ofdm_load_const;
  assert(length(tx_sym_lin) == Nbitsperpacket/bps);

  % place data symbols in multi-carrier frame with pilots and boundary carriers

  s = 1; tx_frame = zeros(Np*Ns,Nc+2);
  for r=1:Np*Ns
    if mod(r-1,Ns) == 0
      % row of pilots
      tx_frame(r,:) = pilots;
    else
      % row of data symbols
      arowofsymbols = tx_sym_lin(s:s+Nc-1);
      tx_frame(r,2:Nc+1) = arowofsymbols;
      s += Nc;
      if states.dpsk
        tx_frame(r,2:Nc+1) = tx_frame(r,2:Nc+1) .* tx_frame(r-1,2:Nc+1);
      end
    end
  end
  % make sure we use all the symbols
  assert((s-1) == length(tx_sym_lin));

  % OFDM upconvert symbol by symbol so we can add CP

  tx = [];
  for r=1:Ns*Np
    asymbol = tx_frame(r,:) * W/M;
    asymbol_cp = [asymbol(M-Ncp+1:M) asymbol];
    tx = [tx asymbol_cp];
  end
endfunction


% -----------------------------------------------------------
% est_timing
% -----------------------------------------------------------

#{
  Correlates known samples (for example pilots or a preamble) with a window of received
  samples to determine the most likely timing offset.  Optionally combines
  known samples from two frames (e.g. pilots at start of this and next frame)
  so we need at least Nsamperframe+M+Ncp samples in rx.

  Can be used for acquisition (coarse timing), and fine timing.  Tends
  to break down when freq offset approaches +/- symbol rate (e.g +/-
  25 Hz for 700D).
#}

function [t_est timing_valid timing_mx av_level] = est_timing(states, rx, known_samples, step, dual=1)
    ofdm_load_const;
    Npsam = length(known_samples);

    Ncorr = length(rx) - (Nsamperframe+Npsam);
    corr = zeros(1,Ncorr);
    %printf("Npsam: %d M+Ncp: %d Ncorr: %d Nsamperframe: %d step: %d\n", Npsam,  M+Ncp, Ncorr, Nsamperframe, step);

    % normalise correlation so we can compare to a threshold across varying input levels

    av_level = 2*sqrt(states.timing_norm*(rx*rx')/length(rx)) + 1E-12;

    % correlate with pilots at start and (optionally) end of frame to determine timing offset

    for i=1:step:Ncorr
      rx1 = rx(i:i+Npsam-1); 
      corr_st = rx1 * known_samples'; 
      corr_en = 0;
      if dual
        % for the streaming voice modes we also correlate with pilot samples at start of next frame 
        rx2 = rx(i+Nsamperframe:i+Nsamperframe+Npsam-1);
        corr_en = rx2 * known_samples';
      end
      corr(i) = (abs(corr_st) + abs(corr_en))/av_level;
    end

    [timing_mx t_est] = max(abs(corr));
    % only declare timing valid if there are enough samples in rxbuf to demodulate a frame
    timing_valid = (abs(rx(t_est)) > 0) && (timing_mx > timing_mx_thresh);
    
    if verbose > 1
      printf("  av_level: %5.4f mx: %4.3f timing_est: %4d timing_valid: %d\n", av_level, timing_mx, t_est, timing_valid);
    end
    if verbose > 2
      figure(10); clf;
      subplot(211); plot(rx)
      subplot(212); plot(corr)
      figure(11); clf; plot(real(known_samples));
    end

endfunction


% -----------------------------------------------------------
% est_freq_offset_known_corr
% -----------------------------------------------------------

#{
  Determines frequency offset at current timing estimate, used for
  coarse freq offset estimation during streaming mode acquisition.
#}

function foff_est = est_freq_offset_known_corr(states, rx, known_samples, t_est, dual=1)
    ofdm_load_const;
    Npsam = length(known_samples);

    % extract pilot samples from either end of frame
    rx1  = rx(t_est:t_est+Npsam-1); rx2 = rx(t_est+Nsamperframe:t_est+Nsamperframe+Npsam-1);

    % "mix" these down (correlate) with 0 Hz offset pilot samples
    corr_st = rx1 .* conj(known_samples);
    if dual 
      corr_en = rx2 .* conj(known_samples);
    end
    
    % sample sum of DFT magnitude of correlated signals at each freq offset and look for peak
    st = -20; en = 20; foff_est = 0; Cabs_max = 0;

    for f=st:en
       w = 2*pi*f/Fs;
       C_st = corr_st * exp(j*w*(0:Npsam-1))';
       C_en = 0;
       if dual
          C_en = corr_en * exp(j*w*(0:Npsam-1))';
       end
       Cabs = abs(C_st) + abs(C_en);
       %printf("f: %4.1f Cabs: %f Cmax: %f\n", f, Cabs, Cabs_max);
       if Cabs > Cabs_max
         Cabs_max = Cabs;
         foff_est = f;
       end
    end

    if states.verbose > 1
      printf("  foff_est: %f\n", foff_est);
    end

endfunction


% Joint estimation used for data mode burst acquistion

function [t_est foff_est timing_mx] = est_timing_and_freq(states, rx, known_samples, tstep, fmin, fmax, fstep)
    ofdm_load_const;
    Npsam = length(known_samples);

    Ncorr = length(rx) - Npsam + 1;
    corr = zeros(1,Ncorr);
    
    % set up matrix of freq shifted known samples for correlation with received signal.  Each row
    % is the known samples shifted by a different freq offset
    
    M = [];
    for afcoarse=fmin:fstep:fmax
       w = 2*pi*afcoarse/Fs;
       wvec = exp(j*w*(0:Npsam-1));
       M = [M; known_samples .* wvec];
    end
    
    % At each timing position, correlate with known samples at all possible freq offsets.  Result
    % is a column vector for each timing offset.  Each matrix cell is s freq,timing coordinate
    
    corr = [];
    for t=1:tstep:Ncorr
      rx1 = rx(t:t+Npsam-1); 
      col = M * rx1';
      corr = [corr, col];
    end
    
    % best timing offset is the col with the global max of the corr matrix
    max_col = max(abs(corr));
    [mx mx_col] = max(max_col);
    t_est = (mx_col-1)*tstep;
    
    % obtain normalised real number for timing mx
    mag1 = known_samples*known_samples';
    mag2 = rx(t_est+1:t_est+Npsam)*rx(t_est+1:t_est+Npsam)';
    timing_mx = mx*mx'/(mag1*mag2+1E-12);
    
    % determine frequency offset for row where max is located
    [tmp freq_row] = max(corr(:,mx_col));
    foff_est = fmin + fstep*(freq_row-1);
       
    if verbose > 1
      printf("  t_est: %d timing:mx: %f foff_est: %f\n", t_est, timing_mx, foff_est);
    end
    if verbose > 2
      figure(10); clf;
      subplot(211); plot(rx)
      subplot(212); plot(corr)
      figure(11); clf; plot(real(known_samples));
    end

endfunction


% streaming mode acquistion, used mainly for voice modes

function [timing_valid states] = ofdm_sync_search_stream(states)
    ofdm_load_const;
    
    st = rxbufst + M+Ncp + Nsamperframe + 1; en = st + 2*Nsamperframe + M+Ncp - 1;
  
    % Attempt coarse timing estimate (i.e. detect start of frame) at a range of frequency offsets

    timing_mx = 0; fcoarse = 0; timing_valid = 0; ct_est = 1;
    for afcoarse=-40:40:40
      % vector of local oscillator samples to shift input vector
      % these could be computed on the fly to save memory, or pre-computed in flash at tables as they are static

      if afcoarse != 0
        w = 2*pi*afcoarse/Fs;
        wvec = exp(-j*w*(0:2*Nsamperframe+M+Ncp-1));

        % choose best timing offset metric at this freq offset
        [act_est atiming_valid atiming_mx] = est_timing(states, wvec .* states.rxbuf(st:en), states.rate_fs_pilot_samples, 2);
      else
        % exp(-j*0) is just 1 when afcoarse is 0
        [act_est atiming_valid atiming_mx] = est_timing(states, states.rxbuf(st:en), states.rate_fs_pilot_samples, 2);
      end

      %printf("afcoarse: %f atiming_mx: %f\n", afcoarse, atiming_mx);

      if atiming_mx > timing_mx
        ct_est = act_est;
        timing_valid = atiming_valid;
        timing_mx = atiming_mx;
        fcoarse = afcoarse;
      end
    end

    % refine freq est within -/+ 20 Hz window

    if fcoarse != 0
      w = 2*pi*fcoarse/Fs;
      wvec = exp(-j*w*(0:2*Nsamperframe+M+Ncp-1));
      foff_est = est_freq_offset_known_corr(states, wvec .* states.rxbuf(st:en), states.rate_fs_pilot_samples, ct_est);
      foff_est += fcoarse;
    else
      % exp(-j*0) is just 1 when fcoarse is 0
      foff_est = est_freq_offset_known_corr(states, states.rxbuf(st:en), states.rate_fs_pilot_samples, ct_est);
    end

    if verbose
      printf(" ct_est: %4d mx: %3.2f coarse_foff: %5.1f timing_valid: %d", ct_est, timing_mx, foff_est, timing_valid);
    end
    
  if timing_valid
    states.nin = ct_est - 1;
  else
    states.nin = Nsamperframe;
  end

  states.timing_valid = timing_valid;
  states.timing_mx = timing_mx;
  states.coarse_foff_est_hz = foff_est;
  states.sample_point = states.timing_est = 1;
endfunction
 

% two stage acquisition detector for burst mode

function results = burst_acquisition_detector(states, rx, n, known_sequence)
  ofdm_load_const;
    
  % initial search over coarse grid
  tstep = 4; fstep = 5;
  [ct_est foff_est timing_mx] = est_timing_and_freq(states, rx(n:n+2*Nsamperframe-1), known_sequence, 
                                                    tstep, fmin = -50, fmax = 50, fstep);
  % refine estimate over finer grid                             
  fmin = foff_est - ceil(fstep/2); fmax = foff_est + ceil(fstep/2); 
  fine_st = max(1, n + ct_est - tstep/2); fine_en = fine_st + Nsamperframe + tstep - 1;
  [ct_est foff_est timing_mx] = est_timing_and_freq(states, rx(fine_st:fine_en), known_sequence, 1, fmin, fmax, 1);
  % refer ct_est to nominal start of frame rx_buf(n)
  ct_est += fine_st - n;
  results.ct_est = ct_est; results.foff_est = foff_est; results.timing_mx = timing_mx;
end


% Burst mode acquisition ------------------------------------------

function [timing_valid states] = ofdm_sync_search_burst(states)
  ofdm_load_const;

  pre_post = "";
  st = rxbufst + M+Ncp + Nsamperframe + 1; en = st + 2*Nsamperframe - 1;
  pre = burst_acquisition_detector(states, states.rxbuf, st, states.tx_preamble);
  if states.postambledetectoren
    post = burst_acquisition_detector(states, states.rxbuf, st, states.tx_postamble);
  end
  
  if isfield(states,"postambletest") pre.timing_mx = 0; end % force ignore preamble to test postamble

  if (states.postambledetectoren == 0) || (pre.timing_mx > post.timing_mx)
    timing_mx = pre.timing_mx; ct_est = pre.ct_est; foff_est = pre.foff_est;
    pre_post = "pre";
  else
    timing_mx = post.timing_mx; ct_est = post.ct_est; foff_est = post.foff_est;
    pre_post = "post";
  end
  timing_valid = timing_mx > timing_mx_thresh;
  
  if timing_valid
    % potential candidate found ....

    % calculate number of samples we need on next buffer to get into sync
    if strcmp(pre_post, "post")
      states.nin = 0;
      % printf("\n  rxbufst: %d ", states.rxbufst);
      states.rxbufst -= states.Np*states.Nsamperframe; % backup to first modem frame in packet
      states.rxbufst += ct_est - 1;
      states.npost++;
      % printf("%d\n", states.rxbufst);
    else
      % ct_est is start of preamble, so advance past that to start of first modem frame
      states.nin = Nsamperframe + ct_est - 1;
      states.npre++;
    end
  else
    states.nin = Nsamperframe;
  end

  states.ct_est = ct_est;
  states.timing_valid = timing_valid;
  states.timing_mx = timing_mx;
  states.sample_point = states.timing_est = 1;
  states.foff_est_hz = foff_est;

  if verbose
    printf("  ct_est: %4d nin: %4d mx: %3.2f foff_est: %5.1f timing_valid: %d %4s", 
           ct_est, states.nin, timing_mx, foff_est, timing_valid, pre_post);
  end
endfunction


% ----------------------------------------------------------------------------------
% ofdm_sync_search - attempts to find coarse sync parameters for modem initial sync
% ----------------------------------------------------------------------------------

function [timing_valid states] = ofdm_sync_search(states, rxbuf_in)
  ofdm_load_const;

  % update rxbuf so it is primed for when we have to call ofdm_demod()

  states.rxbuf(1:Nrxbuf-states.nin) = states.rxbuf(states.nin+1:Nrxbuf);
  states.rxbuf(Nrxbuf-states.nin+1:Nrxbuf) = rxbuf_in;
  
  if strcmp(states.data_mode, "burst")
    [timing_valid states] = ofdm_sync_search_burst(states);
  else
    [timing_valid states] = ofdm_sync_search_stream(states);
  end
endfunction


% ------------------------------------------
% ofdm_demod - Demodulates one frame of bits
% ------------------------------------------

#{

  For phase estimation we need to maintain buffer of 3 frames plus
  one pilot, so we have 4 pilots total. '^' is the start of current
  frame that we are demodulating.

  P DDD P DDD P DDD P
        ^

  Then add one symbol either side to account for movement in
  sampling instant due to sample clock differences:

  D P DDD P DDD P DDD P D
          ^

  Returns:
    rx_bits    - (hard decoded/raw/uncoded) demodulated data bits from packet
    aphase_est - phase est for each data symbol
    rx_np      - output data symbols after phase correction
    rx_amp     - amplitude estimates for each symbol
#}

function [states rx_bits achannel_est_rect_log rx_np rx_amp] = ofdm_demod(states, rxbuf_in)
  ofdm_load_const;

  % insert latest input samples into rxbuf

  rxbuf(1:Nrxbuf-states.nin) = rxbuf(states.nin+1:Nrxbuf);
  rxbuf(Nrxbuf-states.nin+1:Nrxbuf) = rxbuf_in;

  % get latest freq offset estimate

  woff_est = 2*pi*foff_est_hz/Fs;

  % update timing estimate --------------------------------------------------

  delta_t = coarse_foff_est_hz = timing_valid = timing_mx = 0;
  if timing_en
    % update timing at start of every frame

    % search for timing in a window centered on timing_est, the window will typically be around 2Ncp wide as we could
    % get a shift of +Ncp or -Ncp if we swing from one delay extreme to another
    st = rxbufst + M+Ncp + Nsamperframe + 1 - floor(ftwindow_width/2) + (timing_est-1);
    en = st + Nsamperframe-1 + M+Ncp + ftwindow_width-1;

    [ft_est timing_valid timing_mx] = est_timing(states, rxbuf(st:en) .* exp(-j*woff_est*(st:en)), rate_fs_pilot_samples, 1);
    % printf("  timing_est: %d ft_est: %d timing_valid: %d timing_mx: %d\n", timing_est, ft_est, timing_valid, timing_mx);

    % if we are in a deep fade timing_valid will not be asserted as ft_est will be garbage, so we don't
    % adjust timing est, just freewheel for now
    if timing_valid
        
      % adjust timing_est based on ft_est    
      timing_est = timing_est + ft_est - ceil(ftwindow_width/2);

      % Track the ideal sampling point, which is Ncp for a multipath signal whose delay varies between 0 and Ncp.  The
      % timing est will be bouncing back and forth due to multipath so we may need to use the upper or lower limit of
      % the timing est to track the ideal sample_point. A good way to explore this algorithm is to disable the feedback
      % loop for nin adjustment below, and look at the plots from ofdm_rx with +ve and -ve sample clock offsets 
      % (sox can be used to resample).  The "4" constants are small guard bands so we don't stumble outside of the CP 
      % due to noise.
      
      delta_t = ft_est - ceil(ftwindow_width/2);           % just used for plotting
      sample_point = max(timing_est+4, sample_point);      % we are at max timing est, so sample point just above
      sample_point = min(timing_est+Ncp-4, sample_point);  % we are at min timing_est, so sample point Ncp above
    end

    if verbose > 1
      printf("  ft_est: %2d mx: %3.2f coarse_foff: %4.1f foff: %4.1f\n", ft_est, timing_mx, coarse_foff_est_hz, foff_est_hz);
    end

  end

  % down convert at current timing instant----------------------------------

   rx_sym = zeros(1+Ns+1+1, Nc+2);

  % previous pilot

  st = rxbufst + M+Ncp + Nsamperframe + (-Ns)*(M+Ncp) + 1 + sample_point; en = st + M - 1;

  for c=1:Nc+2
    acarrier = rxbuf(st:en) .* exp(-j*woff_est*(st:en)) .* conj(W(c,:));
    rx_sym(1,c) = sum(acarrier);
  end

  % pilot - this frame - pilot

  for rr=1:Ns+1
    st = rxbufst + M+Ncp + Nsamperframe + (rr-1)*(M+Ncp) + 1 + sample_point; en = st + M - 1;
    for c=1:Nc+2
      acarrier = rxbuf(st:en) .* exp(-j*woff_est*(st:en)) .* conj(W(c,:));
      rx_sym(rr+1,c) = sum(acarrier);
    end
  end

  % next pilot

  st = rxbufst + M+Ncp + Nsamperframe + (2*Ns)*(M+Ncp) + 1 + sample_point; en = st + M - 1;
  for c=1:Nc+2
    acarrier = rxbuf(st:en) .* exp(-j*woff_est*(st:en)) .* conj(W(c,:));
    rx_sym(Ns+3,c) = sum(acarrier);
  end

  % est freq err based on all carriers ------------------------------------

  if foff_est_en
    freq_err_rect = sum(rx_sym(2,:))' * sum(rx_sym(2+Ns,:));

    % prevent instability in atan(im/re) when real part near 0

    freq_err_rect += 1E-6;

    %printf("freq_err_rect: %f %f angle: %f\n", real(freq_err_rect), imag(freq_err_rect), angle(freq_err_rect));
    freq_err_hz = angle(freq_err_rect)*Rs/(2*pi*Ns);
    if states.foff_limiter
      freq_err_hz = max(freq_err_hz,-1);
      freq_err_hz = min(freq_err_hz, 1);
    end
    foff_est_hz = foff_est_hz + foff_est_gain*freq_err_hz;
  end

  % OK - now channel for each carrier and correct phase  ----------------------------------

  achannel_est_rect = zeros(1,Nc+2);
  aamp_est_pilot = zeros(1,Nc+2);
  for c=2:Nc+1

    % estimate channel for this carrier using an average of 12 pilots
    % in a rect 2D window centred on this carrier

    % PPP  <-- frame-1
    % ---
    % PPP  <-- you are here
    % DDD
    % DDD
    % PPP  <-- frame+1
    % ---
    % PPP  <-- frame+2

    if isfield(states, "phase_est_bandwidth")
      phase_est_bandwidth = states.phase_est_bandwidth;
    else
      phase_est_bandwidth = "low";
    end

    if strcmp(phase_est_bandwidth, "high")
      % Only use pilots at start and end of this frame to track quickly changes in phase
      % present.  Useful for initial sync where freq offset est may be a bit off, and
      % for high Doppler channels.  As less pilots are averaged, low SNR performance
      % will be poorer.
      achannel_est_rect(c) =  rx_sym(2,c)*pilots(c)';        % frame
      achannel_est_rect(c) += rx_sym(2+Ns,c)*pilots(c)';     % frame+1
      aamp_est_pilot(c) = abs(rx_sym(2,c)) + abs(rx_sym(2+Ns,c));
    elseif strcmp(phase_est_bandwidth, "low")
      % Average over a bunch of pilots in adjacent carriers, and past and future frames, good
      % low SNR performance, but will fall over with high Doppler or freq offset.
      cr = c-1:c+1;
      achannel_est_rect(c) =  rx_sym(2,cr)*pilots(cr)';      % frame
      achannel_est_rect(c) += rx_sym(2+Ns,cr)*pilots(cr)';   % frame+1
      aamp_est_pilot(c)  = sum(abs(rx_sym(2,cr)));
      aamp_est_pilot(c) += sum(abs(rx_sym(2+Ns,cr)));

      % use next step of pilots in past and future

      achannel_est_rect(c) += rx_sym(1,cr)*pilots(cr)';      % frame-1
      achannel_est_rect(c) += rx_sym(2+Ns+1,cr)*pilots(cr)'; % frame+2
      aamp_est_pilot(c) += sum(abs(rx_sym(1,cr)));
      aamp_est_pilot(c) += sum(abs(rx_sym(2+Ns+1,cr)));
    end
  end

  % pilots are estimated over 12 pilot symbols, so find average

  if strcmp(phase_est_bandwidth, "high")
    achannel_est_rect /= 2;
    aamp_est_pilot /= 2;
  elseif strcmp(phase_est_bandwidth, "low")
    achannel_est_rect /= 12;
    aamp_est_pilot /= 12;
  end

  aphase_est_pilot = angle(achannel_est_rect);
  if states.amp_est_mode == 0
    % legacy 700D/2020 ampl estimator for compatability with current C code
    aamp_est_pilot = abs(achannel_est_rect);
  end
  achannel_est_rect = aamp_est_pilot.*exp(j*aphase_est_pilot);

  % correct phase offset using phase estimate, and demodulate
  % bits, separate loop as it runs across cols (carriers) to get
  % frame bit ordering correct

  rx_bits = []; rx_np = []; rx_amp = []; achannel_est_rect_log = [];
  for rr=1:Ns-1
    for c=2:Nc+1
      if phase_est_en
        if states.dpsk
          rx_corr = rx_sym(rr+2,c) *  rx_sym(rr+1,c)';
        else
          rx_corr = rx_sym(rr+2,c) * exp(-j*aphase_est_pilot(c));
        end
      else
        rx_corr = rx_sym(rr+2,c);
      end

      rx_np = [rx_np rx_corr];
      rx_amp = [rx_amp aamp_est_pilot(c)];

      % hard decision demod
      if bps == 1 abit = real(rx_corr) > 0; end
      if bps == 2 abit = qpsk_demod(rx_corr); end
      if bps == 4 abit = qam16_demod(states.qam16, rx_corr, max(1E-12,aamp_est_pilot(c))); end
      rx_bits = [rx_bits abit];
    end % c=2:Nc+1
    achannel_est_rect_log = [achannel_est_rect_log; achannel_est_rect(2:Nc+1)];
  end

  % Adjust nin to take care of sample clock offset.  When debugong or exploring how timing loop works
  % it's a good idea to comment out ths code to "open the loop".

  nin = Nsamperframe;
  if timing_en && timing_valid
    states.clock_offset_est = 0.9*states.clock_offset_est + 0.1*abs(states.timing_est - timing_est)/Nsamperframe;
    thresh = (M+Ncp)/8;
    tshift = (M+Ncp)/4;
    if timing_est > thresh
      nin = Nsamperframe+tshift;
      timing_est -= tshift;
      sample_point -= tshift;
    end
    if timing_est < -thresh
      nin = Nsamperframe-tshift;
      timing_est += tshift;
      sample_point += tshift;
    end
  end

  % use internal rxbuf samples if they are available
  rxbufst_next = rxbufst + nin;
  %printf("\nrxbufst: %d rxbufst_next: %d nin: %d Nrxbufmin: %d rqd: %d Nrxbuf: %d\n", 
  %     rxbufst, rxbufst_next, nin, Nrxbufmin, rxbufst_next + Nrxbufmin, Nrxbuf);
  if rxbufst_next + Nrxbufmin <= Nrxbuf
     % printf("Can maybe use rxbufst!\n");
     rxbufst = rxbufst_next;
     nin = 0;
  end
      
  % maintain mean amp estimate for LDPC decoder
  states.mean_amp = 0.9*states.mean_amp + 0.1*mean(rx_amp);

  states.rx_sym = rx_sym;
  states.rxbuf = rxbuf;
  states.nin = nin;
  states.rxbufst = rxbufst;
  states.timing_valid = timing_valid;
  states.timing_mx = timing_mx;
  states.timing_est = timing_est;
  states.sample_point = sample_point;
  states.delta_t = delta_t;
  states.foff_est_hz = foff_est_hz;
  states.coarse_foff_est_hz = coarse_foff_est_hz; % just used for tofdm
endfunction


function SNR3kdB = snr_from_esno(states, EsNodB)
    ofdm_load_const;

    % We integrate over M samples to get the received symbols.  Additional signal power
    % is used for the cyclic prefix samples.
    cyclic_power = 10*log10((Ncp+M)/M);
    % Es is the energy for each symbol.  To get signal power lets
    % multiply by symbols/second, and calculate noise power in 3000 Hz.
    SNR3kdB = EsNodB + 10*log10(Nc*Rs/3000) + cyclic_power;
endfunction

% ----------------------------------------------------------------------------------
% assemble_modem_packet - assemble modem packet from UW, payload, and txt bits
% ----------------------------------------------------------------------------------

function modem_frame = assemble_modem_packet(states, payload_bits, txt_bits)
  ofdm_load_const;

  # Due to the operation of the FEC encoder or interleaver, Tx data
  # usually comes in "packet size" chunks, so assembly operates on an
  # entire packet (multiple modem frames if Np>1)

  p = 1; u = 1;
  modem_frame = zeros(1,Nbitsperpacket);

  for b=1:Nbitsperpacket-Ntxtbits;
    if (u <= Nuwbits) && (b == uw_ind(u))
      modem_frame(b) = tx_uw(u++);
    else
      modem_frame(b) = payload_bits(p++);
    end
  end
  t = 1;
  for b=Nbitsperpacket-Ntxtbits+1:Nbitsperpacket
    modem_frame(b) = txt_bits(t++);
  end
  assert(u == (Nuwbits+1));
  assert(p = (length(payload_bits)+1));
endfunction


% ----------------------------------------------------------------------------------
% assemble_modem_packet_symbols - assemble modem packet from UW, payload, and txt bits
% ----------------------------------------------------------------------------------

function modem_frame = assemble_modem_packet_symbols(states, payload_syms, txt_syms)
  ofdm_load_const;

  Nsymsperpacket = Nbitsperpacket/bps;
  Nuwsyms = Nuwbits/bps;
  Ntxtsyms = Ntxtbits/bps;
  modem_frame = zeros(1,Nsymsperpacket);
  p = 1; u = 1;

  for s=1:Nsymsperpacket-Ntxtsyms;
    if (u <= Nuwsyms) && (s == uw_ind_sym(u))
      modem_frame(s) = states.tx_uw_syms(u++);
    else
      modem_frame(s) = payload_syms(p++);
    end
  end
  t = 1;
  for s=Nsymsperpacket-Ntxtsyms+1:Nsymsperpacket
    modem_frame(s) = txt_syms(t++);
  end
  assert(u == (Nuwsyms+1));
  assert(p = (length(payload_syms)+1));
endfunction


% ------------------------------------------------------------------------------------------------
% extract_uw - extract just the UW from the first few frames of a packet, to check UW
%              during acquisition
% -------------------------------------------------------------------------------------------------

function rx_uw = extract_uw(states, rx_syms, rx_amps)
  ofdm_load_const;

  Nsymsperframe = Nbitsperframe/bps;
  assert(length(rx_syms) == Nuwframes*Nsymsperframe);
  Nuwsyms = Nuwbits/bps;
  rx_uw_syms = zeros(1,Nuwsyms);
  rx_uw_amps = zeros(1,Nuwsyms);
  u = 1;

  for s=1:Nuwframes*Nsymsperframe
    if (u <= Nuwsyms) && (s == uw_ind_sym(u))
      rx_uw_syms(u) = rx_syms(s);
      rx_uw_amps(u) = rx_amps(s);
      u++;
    end
  end
  assert(u == (Nuwsyms+1));

  % now demodulate UW bits
  rx_uw = zeros(1,Nuwbits);

  for s=1:Nuwsyms
    if bps == 2
      rx_uw(bps*(s-1)+1:bps*s) = qpsk_demod(rx_uw_syms(s));
    elseif bps == 4
      rx_uw(bps*(s-1)+1:bps*s) = qam16_demod(states.qam16,rx_uw_syms(s), max(1E-12,rx_amps(s)));
    end
  end
endfunction


% ------------------------------------------------------------------------------------------------
% disassemble_modem_packet - extract UW, txt bits, and payload symbols from a packet of symbols
% -------------------------------------------------------------------------------------------------

function [rx_uw payload_syms payload_amps txt_bits] = disassemble_modem_packet(states, modem_frame_syms, modem_frame_amps)
  ofdm_load_const;

  Nsymsperpacket = Nbitsperpacket/bps;
  Nuwsyms = Nuwbits/bps;
  Ntxtsyms = Ntxtbits/bps;
  payload_syms = zeros(1,Nsymsperpacket-Nuwsyms-Ntxtsyms);
  payload_amps = zeros(1,Nsymsperpacket-Nuwsyms-Ntxtsyms);
  rx_uw_syms = zeros(1,Nuwsyms);
  rx_uw_amps = zeros(1,Nuwsyms);
  txt_syms = zeros(1,Ntxtsyms);
  p = 1; u = 1;

  for s=1:Nsymsperpacket-Ntxtsyms;
    if (u <= Nuwsyms) && (s == uw_ind_sym(u))
      rx_uw_syms(u) = modem_frame_syms(s);
      rx_uw_amps(u) = modem_frame_amps(s);
      u++;
    else
      payload_syms(p) = modem_frame_syms(s);
      payload_amps(p++) = modem_frame_amps(s);
    end
  end
  t = 1;
  for s=Nsymsperpacket-Ntxtsyms+1:Nsymsperpacket
    txt_syms(t++) = modem_frame_syms(s);
  end
  assert(u == (Nuwsyms+1));
  assert(p = (Nsymsperpacket+1));

  % now demodulate UW and txt bits

  rx_uw = zeros(1,Nuwbits);
  txt_bits = zeros(1,Ntxtbits);

  for s=1:Nuwsyms
    if bps == 2
      rx_uw(bps*(s-1)+1:bps*s) = qpsk_demod(rx_uw_syms(s));
    elseif bps == 4
      rx_uw(bps*(s-1)+1:bps*s) = qam16_demod(states.qam16,rx_uw_syms(s),rx_uw_amps(s));
    end
  end
  for s=1:Ntxtsyms
    txt_bits(2*s-1:2*s) = qpsk_demod(txt_syms(s));
  end

endfunction


%-----------------------------------------------------------------------
% ofdm_rand - a psuedo-random number generator that we can implement
%             in C with identical results to Octave.  Returns an unsigned
%             int between 0 and 32767
%-----------------------------------------------------------------------

function r = ofdm_rand(n, seed=1)
  r = zeros(1,n);
  for i=1:n
    seed = mod(1103515245 * seed + 12345, 32768);
    r(i) = seed;
  end
endfunction


% build a single modem frame preamble vector for reliable single frame acquisition
% on data modes
function tx_preamble = ofdm_generate_preamble(states, seed=2)
  tmp_states = states;
  % tweak local copy of states so we can generate a 1 modem-frame packet
  tmp_states.Np = 1; tmp_states.Nbitsperpacket = tmp_states.Nbitsperframe;
  preamble_bits = ofdm_rand(tmp_states.Nbitsperframe, seed) > 16384;
  tx_preamble = ofdm_mod(tmp_states, preamble_bits);
endfunction


%-----------------------------------------------------------------------
% create_ldpc_test_frame - generate a test frame of bits
%-----------------------------------------------------------------------

function [tx_bits payload_data_bits codeword] = create_ldpc_test_frame(states, coded_frame=1)
  ofdm_load_const;
  ldpc;
  gp_interleaver;

  if coded_frame
    % Set up LDPC code

    mod_order = 4; bps = 2; modulation = 'QPSK'; mapping = 'gray';

    init_cml(); % TODO: make this path sensible and portable
    load HRA_112_112.txt
    [code_param framesize rate] = ldpc_init_user(HRA_112_112, modulation, mod_order, mapping);
    assert(Nbitsperframe == (code_param.coded_bits_per_frame + Nuwbits + Ntxtbits));

    payload_data_bits = round(ofdm_rand(code_param.data_bits_per_frame)/32767);
    codeword = LdpcEncode(payload_data_bits, code_param.H_rows, code_param.P_matrix);
    Nsymbolsperframe = length(codeword)/bps;

    % need all these steps to get actual raw codeword bits at demod ..

    tx_symbols = [];
    for s=1:Nsymbolsperframe
      tx_symbols = [tx_symbols qpsk_mod( codeword(2*(s-1)+1:2*s) )];
    end

    tx_symbols = gp_interleave(tx_symbols);

    codeword_raw = [];
    for s=1:Nsymbolsperframe
      codeword_raw = [codeword_raw qpsk_demod(tx_symbols(s))];
    end
  else
    codeword_raw = round(ofdm_rand(Nbitsperpacket-(Nuwbits+Ntxtbits))/32767);
  end

  % insert UW and txt bits

  tx_bits = assemble_modem_packet(states, codeword_raw, zeros(1,Ntxtbits));
  assert(Nbitsperpacket == length(tx_bits));

endfunction

% automated test

function test_assemble_disassemble(states)
  ofdm_load_const;

  Nsymsperpacket = Nbitsperpacket/bps;
  Ndatabitsperpacket = Nbitsperpacket-(Nuwbits+Ntxtbits);
  Ndatasymsperpacket = Ndatabitsperpacket/bps;
  codeword_bits = round(ofdm_rand(Ndatabitsperpacket)/32767);
  tx_bits = assemble_modem_packet(states, codeword_bits, zeros(1,Ntxtbits));

  tx_syms = zeros(1,Nsymsperpacket);
  for s=1:Nsymsperpacket
    if bps == 2
      tx_syms(s) = qpsk_mod(tx_bits(bps*(s-1)+1:bps*s));
    elseif bps == 4
      tx_syms(s) = qam16_mod(states.qam16,tx_bits(bps*(s-1)+1:bps*s));
    end
  end
  codeword_syms = zeros(1,Ndatasymsperpacket);
  for s=1:Ndatasymsperpacket
    if bps == 2
      codeword_syms(s) = qpsk_mod(codeword_bits(bps*(s-1)+1:bps*s));
    elseif bps == 4
      codeword_syms(s) = qam16_mod(states.qam16,codeword_bits(bps*(s-1)+1:bps*s));
    end
  end

  [rx_uw rx_codeword_syms payload_amps txt_bits] = disassemble_modem_packet(states, tx_syms, ones(1,Nsymsperpacket));
  assert(rx_uw == states.tx_uw);
  Ndatasymsperframe = (Nbitsperpacket-(Nuwbits+Ntxtbits))/bps;
  assert(codeword_syms == rx_codeword_syms);
endfunction

%-------------------------------------------------------------------
% sync_state_machine - calls mode-specific sync state state_machine
%-------------------------------------------------------------------

function states = sync_state_machine(states, rx_uw)
  if strcmp(states.state_machine, "voice1")
    states = sync_state_machine_voice1(states, rx_uw);
  elseif strcmp(states.state_machine, "data")
    if strcmp(states.data_mode, "streaming")
      states = sync_state_machine_data_streaming(states, rx_uw);
    else
      states = sync_state_machine_data_burst(states, rx_uw);
    end
  elseif strcmp(states.state_machine, "voice2")
    states = sync_state_machine_voice2(states, rx_uw);
  else
    assert(0);
  endif
endfunction

%--------------------------------------------------------------------
%  Due to the low pilot symbol insertion rate and acquisition issues
%  the earlier OFDM modem waveforms (700D and 2020) need a complex
%  state machine to help them avoid false sync.
%--------------------------------------------------------------------

function states = sync_state_machine_voice1(states, rx_uw)
  ofdm_load_const;
  next_state = states.sync_state;
  states.sync_start = states.sync_end = 0;

  if strcmp(states.sync_state,'search')

    if states.timing_valid
      states.frame_count = 0;
      states.sync_counter = 0;
      states.modem_frame = 0;
      states.sync_start = 1;
      next_state = 'trial';
    end
  end

  if strcmp(states.sync_state,'synced') || strcmp(states.sync_state,'trial')

    states.frame_count++;

    % UW occurs at the start of a packet
    if states.modem_frame == 0
        states.uw_errors = sum(xor(tx_uw,rx_uw));

        if strcmp(states.sync_state,'trial')
          if states.uw_errors >= states.bad_uw_errors
            states.sync_counter++;
            states.frame_count = 0;
          end
          if states.sync_counter == 2
            next_state = "search";
            states.phase_est_bandwidth = "high";
          end
          if states.frame_count == 4
            next_state = "synced";
            % change to low bandwidth, but more accurate phase estimation
            states.phase_est_bandwidth = "low";
          end
          if states.uw_errors < 2
            next_state = "synced";
            % change to low bandwidth, but more accurate phase estimation
            states.phase_est_bandwidth = "low";
          else
            next_state = "search";
          end
        end

        if strcmp(states.sync_state,'synced')
          if states.uw_errors > 2
            states.sync_counter++;
          else
            states.sync_counter = 0;
          end

          if states.sync_counter == 6
            next_state = "search";
            states.phase_est_bandwidth = "high";
          end
        end
      end % if modem_frame == 0 ....

      % keep track of where we are up to in packet
      states.modem_frame++;
      if (states.modem_frame >= states.Np) states.modem_frame = 0; end
  end

  states.last_sync_state = states.sync_state;
  states.sync_state = next_state;
endfunction


%-------------------------------------------------------
% data (streaming mode) state machine
%-------------------------------------------------------

function states = sync_state_machine_data_streaming(states, rx_uw)
  ofdm_load_const;
  next_state = states.sync_state;
  states.sync_start = states.sync_end = 0;

  if strcmp(states.sync_state,'search')
    if states.timing_valid
      states.sync_start = 1; 
      states.sync_counter = 0;
      next_state = 'trial';
    end
  end

  states.uw_errors = sum(xor(tx_uw,rx_uw));

  if strcmp(states.sync_state,'trial')
    if states.uw_errors < states.bad_uw_errors;
      next_state = "synced";
      states.packet_count = 0;
      states.modem_frame = Nuwframes;
    else
      states.sync_counter++;
      if states.sync_counter > Np
        next_state = "search";
      end
    end
  end
 
  % Note packetsperburst==0 we don't ever lose sync, which is useful for 
  % stream based testing or external control of state machine
  
  if strcmp(states.sync_state,'synced')
    states.modem_frame++;
    if (states.modem_frame >= states.Np) 
      states.modem_frame = 0; 
      states.packet_count++;
      if (states.packetsperburst)
        if (states.packet_count >= states.packetsperburst)
          next_state = "search";
        end
      end
    end
  end
  
  states.last_sync_state = states.sync_state;
  states.sync_state = next_state;
endfunction

%-------------------------------------------------------
% data (burst mode) state machine
%-------------------------------------------------------

function states = sync_state_machine_data_burst(states, rx_uw)
  ofdm_load_const;
  next_state = states.sync_state;
  states.sync_start = states.sync_end = 0;

  if strcmp(states.sync_state,'search')
    if states.timing_valid
      states.sync_start = 1; 
      states.sync_counter = 0;
      next_state = 'trial';
    end
  end

  states.uw_errors = sum(xor(tx_uw,rx_uw));

  % pre or post-amble has told us this is the start of the packet.  Confirm we 
  % have a valid frame by checking the UW after the modem frames containing
  % the UW have been received 
  if strcmp(states.sync_state,'trial')
    states.sync_counter++;
    if states.sync_counter == Nuwframes
      if states.uw_errors < states.bad_uw_errors;
        next_state = "synced";
        states.packet_count = 0;                          % number of packets in this burst
        states.modem_frame = Nuwframes;                   % which modem frame we are up to in packet
      else
        next_state = "search";
        % reset rxbuf to make sure we only ever do a postamble loop once through same samples
        states.rxbufst = states.Nrxbufhistory;
        states.rxbuf = zeros(1, states.Nrxbuf);
      end
    end
  end
  
  if strcmp(states.sync_state,'synced')
    states.modem_frame++;
    if (states.modem_frame >= states.Np) 
      states.modem_frame = 0;                           % start of new packet
      states.packet_count++;
      if (states.packetsperburst)
        if (states.packet_count >= states.packetsperburst)
          next_state = "search";                        % we've finished this burst
          % reset rxbuf to make sure we only ever do a postamble loop once through same samples
          states.rxbufst = states.Nrxbufhistory;
          states.rxbuf = zeros(1, states.Nrxbuf);
        end
      end
    end
  end
  
  states.last_sync_state = states.sync_state;
  states.sync_state = next_state;
endfunction

%-------------------------------------------------------
% fast sync voice state state_machine
%-------------------------------------------------------

function states = sync_state_machine_voice2(states, rx_uw)
  ofdm_load_const;
  next_state = states.sync_state;
  states.sync_start = states.sync_end = 0;

  if strcmp(states.sync_state,'search')

    if states.timing_valid
      states.frame_count = 0;
      states.sync_counter = 0;
      states.modem_frame = 0;
      states.sync_start = 1;
      next_state = 'trial';
    end
  end

  if strcmp(states.sync_state,'synced') || strcmp(states.sync_state,'trial')

    states.frame_count++;

    % UW occurs at the start of a packet
    if states.modem_frame == 0
        states.uw_errors = sum(xor(tx_uw,rx_uw));

        if strcmp(states.sync_state,'trial')
          if states.uw_errors <= states.bad_uw_errors
            next_state = "synced";
          else
            next_state = "search";
          end
        end

        if strcmp(states.sync_state,'synced')
          if states.uw_errors > states.bad_uw_errors
            states.sync_counter++;
          else
            states.sync_counter = 0;
          end

          if states.sync_counter == 6
            next_state = "search";
          end
        end
      end

      % keep track of where we are up to in packet
      states.modem_frame++;
      if (states.modem_frame >= states.Np) states.modem_frame = 0; end
  end

  states.last_sync_state = states.sync_state;
  states.sync_state = next_state;
endfunction


% ------------------------------------------------------------------------------
% codec_to_frame_packing - Set up a bunch of constants to support modem frame
%                          construction from LDPC codewords and codec source bits
% ------------------------------------------------------------------------------

function [code_param Nbitspercodecframe Ncodecframespermodemframe] = codec_to_frame_packing(states, mode)
  ofdm_load_const;
  mod_order = 4; bps = 2; modulation = 'QPSK'; mapping = 'gray';

  init_cml();
  if strcmp(mode, "700D")
    load HRA_112_112.txt
    code_param = ldpc_init_user(HRA_112_112, modulation, mod_order, mapping);
    assert(Nbitsperframe == (code_param.coded_bits_per_frame + Nuwbits + Ntxtbits));
    % unused for this mode
    Nbitspercodecframe = Ncodecframespermodemframe = 0;
  end
  if strcmp(mode, "700E")
    load HRA_56_56.txt
    code_param = ldpc_init_user(HRA_56_56, modulation, mod_order, mapping);
    assert(Nbitsperframe == (code_param.coded_bits_per_frame + Nuwbits + Ntxtbits));
    % unused for this mode
    Nbitspercodecframe = Ncodecframespermodemframe = 0;
  end
  if strcmp(mode, "2020")
    load HRA_504_396.txt
    code_param = ldpc_init_user(HRA_504_396, modulation, mod_order, mapping);
    code_param.data_bits_per_frame = 312;
    code_param.coded_bits_per_frame = code_param.data_bits_per_frame + code_param.ldpc_parity_bits_per_frame;
    code_param.coded_syms_per_frame = code_param.coded_bits_per_frame/code_param.bits_per_symbol;
    printf("2020 mode\n");
    printf("ldpc_data_bits_per_frame = %d\n", code_param.ldpc_data_bits_per_frame);
    printf("ldpc_coded_bits_per_frame  = %d\n", code_param.ldpc_coded_bits_per_frame);
    printf("ldpc_parity_bits_per_frame  = %d\n", code_param.ldpc_parity_bits_per_frame);
    printf("data_bits_per_frame = %d\n", code_param.data_bits_per_frame);
    printf("coded_bits_per_frame  = %d\n", code_param.coded_bits_per_frame);
    printf("coded_syms_per_frame  = %d\n", code_param.coded_syms_per_frame);
    printf("ofdm_bits_per_frame  = %d\n", Nbitsperframe);
    Nbitspercodecframe = 52; Ncodecframespermodemframe = 6;
    printf("  Nuwbits: %d  Ntxtbits: %d\n", Nuwbits, Ntxtbits);
    Nparity = code_param.ldpc_parity_bits_per_frame;
    totalbitsperframe = code_param.data_bits_per_frame + Nparity + Nuwbits + Ntxtbits;
    printf("Total bits per frame: %d\n", totalbitsperframe);
    assert(totalbitsperframe == Nbitsperframe);
  end
  if strcmp(mode, "qam16c1")
      load H2064_516_sparse.mat
      code_param = ldpc_init_user(HRA, modulation='QAM', mod_order=16, mapping="", reshape(states.qam16,1,16));
  end
  if strcmp(mode, "qam16c2")
      framesize = 16200; rate = 0.6;
      code_param = ldpc_init_builtin("dvbs2", rate, framesize, modulation='QAM', mod_order=16, mapping="", reshape(states.qam16,1,16));
  end
  if strcmp(mode, "datac0")
    load H_128_256_5.mat
    code_param = ldpc_init_user(H, modulation, mod_order, mapping);
  end
  if strcmp(mode, "datac1")
    load H_4096_8192_3d.mat
    code_param = ldpc_init_user(HRA, modulation, mod_order, mapping);
  end
  if strcmp(mode, "datac3")
    load H_1024_2048_4f.mat
    code_param = ldpc_init_user(H, modulation, mod_order, mapping);
  end
  if strcmp(mode, "datac0") || strcmp(mode, "datac1") || strcmp(mode, "datac3") || strcmp(mode, "qam16c1") || strcmp(mode, "qam16c2")
    printf("ldpc_data_bits_per_frame = %d\n", code_param.ldpc_data_bits_per_frame);
    printf("ldpc_coded_bits_per_frame  = %d\n", code_param.ldpc_coded_bits_per_frame);
    printf("ldpc_parity_bits_per_frame  = %d\n", code_param.ldpc_parity_bits_per_frame);
    printf("Nbitsperpacket  = %d\n", Nbitsperpacket);
    Nparity = code_param.ldpc_parity_bits_per_frame;
    totalbitsperframe = code_param.data_bits_per_frame + Nparity + Nuwbits + Ntxtbits;
    printf("totalbitsperframe = %d\n", totalbitsperframe);
    assert(totalbitsperframe == Nbitsperpacket);
    Nbitspercodecframe = Ncodecframespermodemframe = -1;
  end
endfunction


% ------------------------------------------------------------------------------
% fec_encode - Handle FEC encoding
% ------------------------------------------------------------------------------

function [frame_bits bits_per_frame] = fec_encode(states, code_param, mode, payload_bits, ...
                                                      Ncodecframespermodemframe, Nbitspercodecframe)
  ofdm_load_const;
  if strcmp(mode, "2020")
    Nunused = code_param.ldpc_data_bits_per_frame - code_param.data_bits_per_frame;
    frame_bits = LdpcEncode([payload_bits zeros(1,Nunused)], code_param.H_rows, code_param.P_matrix);
    % remove unused data bits
    frame_bits = [ frame_bits(1:code_param.data_bits_per_frame) frame_bits(code_param.ldpc_data_bits_per_frame+1:end) ];
  else
    frame_bits = LdpcEncode(payload_bits, code_param.H_rows, code_param.P_matrix);
  end
  bits_per_frame = length(frame_bits);

endfunction


% test function, kind of like a CRC for QPSK symbols, to compare two vectors

function acc = test_acc(v)
  sre = 0; sim = 0;
  for i=1:length(v)
    x = v(i);
    re = round(real(x)); im = round(imag(x));
    sre += re; sim += im;
    %printf("%d %10f %10f %10f %10f\n", i, re, im, sre, sim);
  end
  acc = sre + j*sim;
end


% Save test bits frame to a text file in the form of a C array
%
% usage:
%   ofdm_lib; test_bits_ofdm_file
%

function test_bits_ofdm_file
  Ts = 0.018; Tcp = 0.002; Rs = 1/Ts; bps = 2; Nc = 17; Ns = 8;
  states = ofdm_init(bps, Rs, Tcp, Ns, Nc);
  [test_bits_ofdm payload_data_bits codeword] = create_ldpc_test_frame(states);
  printf("%d test bits\n", length(test_bits_ofdm));

  f=fopen("../src/test_bits_ofdm.h","wt");
  fprintf(f,"/* Generated by test_bits_ofdm_file() Octave function */\n\n");
  fprintf(f,"const int test_bits_ofdm[]={\n");
  for m=1:length(test_bits_ofdm)-1
    fprintf(f,"  %d,\n",test_bits_ofdm(m));
  endfor
  fprintf(f,"  %d\n};\n",test_bits_ofdm(end));

  fprintf(f,"\nconst int payload_data_bits[]={\n");
  for m=1:length(payload_data_bits)-1
    fprintf(f,"  %d,\n",payload_data_bits(m));
  endfor
  fprintf(f,"  %d\n};\n",payload_data_bits(end));

  fprintf(f,"\nconst int test_codeword[]={\n");
  for m=1:length(codeword)-1
    fprintf(f,"  %d,\n",codeword(m));
  endfor
  fprintf(f,"  %d\n};\n",codeword(end));

  fclose(f);

endfunction


% Get rid of nasty unfiltered stuff either side of OFDM signal
% This may need to be tweaked, or better yet made a function of Nc, if Nc changes
%
% usage:
%  ofdm_lib; make_ofdm_bpf(1);

function bpf_coeff = make_ofdm_bpf(write_c_header_file)
  filt_n = 100;
  Fs = 8000;

  bpf_coeff  = fir2(filt_n,[0 900 1000 2000 2100 4000]/(Fs/2),[0.001 0.001 1 1 0.001 0.001]);

  if write_c_header_file
    figure(1)
    clf;
    h = freqz(bpf_coeff,1,Fs/2);
    plot(20*log10(abs(h)))
    grid minor

    % save coeffs to a C header file

    f=fopen("../src/ofdm_bpf_coeff.h","wt");
    fprintf(f,"/* 1000 - 2000 Hz FIR filter coeffs */\n");
    fprintf(f,"/* Generated by make_ofdm_bpf() in ofdm_lib.m */\n");

    fprintf(f,"\n#define OFDM_BPF_N %d\n\n", filt_n);

    fprintf(f,"float ofdm_bpf_coeff[]={\n");
    for r=1:filt_n
      if r < filt_n
        fprintf(f, "  %f,\n",  bpf_coeff(r));
      else
        fprintf(f, "  %f\n};", bpf_coeff(r));
      end
    end
    fclose(f);
  end

endfunction


% returns level threshold such that threshold_cdf of the tx magnitudes are beneath that level
function threshold_level = ofdm_determine_clip_threshold(tx, threshold_cdf)
  Nsteps = 25;
  mx = max(abs(tx));
  cdf = empirical_cdf(mx*(1:Nsteps)/Nsteps,abs(tx));
  threshold_level = find(cdf >= threshold_cdf)(1)*mx/25;
  printf("threshold_cdf: %f threshold_level: %f\n", threshold_cdf, threshold_level);
  figure(1); clf; [hh nn] = hist(abs(tx),Nsteps,1);
  plotyy(nn,hh,mx*(1:Nsteps)/Nsteps,cdf); title('PDF and CDF Estimates'); grid;
end


function [tx nclipped] = ofdm_clip(states, tx, threshold_level, plot_en=0)
  ofdm_load_const;
  tx_ = tx;
  ind = find(abs(tx) > threshold_level);
  nclipped = length(ind);
  tx(ind) = threshold_level*exp(j*angle(tx(ind)));
  if plot_en
    figure(2); clf; plot(abs(tx_(1:5*M))); hold on; plot(abs(tx(1:5*M))); hold off;
  endif
end

% two stage Hilbert clipper to improve PAPR 
function tx = ofdm_hilbert_clipper(states, tx, tx_clip_en)
  tx *= states.amp_scale;
  
  % optional compressor to improve PAPR

  nclipped = 0;
  if tx_clip_en
    if states.verbose
      printf("%f %f\n", states.clip_gain1, states.clip_gain2);
    end
    [tx nclipped] = ofdm_clip(states, tx*states.clip_gain1, states.ofdm_peak);
    
    % BPF, we actually shift the signal back down to baseband to filter
    ssbfilt_n = 100;
    ssbfilt_coeff = fir1(ssbfilt_n, states.txbpf_width_Hz/states.Fs);
    lo = exp(j*2*pi*states.fcentre*(1:length(tx))/(states.Fs));
    tx = lo.*filter(ssbfilt_coeff,1,tx.*conj(lo));
    
    % filter messs up peak levels use this to get us back to approx 16384
    tx *= states.clip_gain2;
  end

  % Hilbert Clipper 2 - remove any really low probability outliers after clipping/filtering
  % even on vanilla Tx
  [tx tmp] = ofdm_clip(states, tx, states.ofdm_peak);

  % note this is PAPR of complex signal, PAPR of real signal will be 3dB-ish larger
  peak = max(abs(tx)); RMS = sqrt(mean(abs(tx).^2));
  cpapr = 10*log10((peak.^2)/(RMS.^2));

  if states.verbose
    printf("Peak: %4.2f RMS: %5.2f CPAPR: %4.1f clipped: %5.2f%%\n",
           peak, RMS, cpapr, nclipped*100/length(tx));
  end
endfunction


%  helper function that adds channel simulation and ensures we don't saturate int16 output samples  
function [rx_real rx] = ofdm_channel(states, tx, SNR3kdB, channel, freq_offset_Hz)
  [rx_real rx sigma] = channel_simulate(states.Fs, SNR3kdB, freq_offset_Hz, channel, tx, states.verbose);
    
  % add a few seconds of no-signal at the start
  rx_real = [sigma*randn(1,states.Fs) rx_real];
  
  % multipath models can lead to clipping of int16 samples
  num_clipped = length(find(abs(rx_real>32767)));
  while num_clipped/length(rx_real) > 0.001
    rx_real /= 2;
    num_clipped = length(find(abs(rx_real>32767)));
    printf("WARNING: output samples clipped, reducing level\n")
  end
endfunction
