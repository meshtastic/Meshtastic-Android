% cohpsk_lib.m
% David Rowe Mar 2015
%
% Coherent PSK modem functions
%

1;

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
    if isscalar(symbol) == 0
        printf("only works with scalars\n");
        return;
    end
    bit0 = real(symbol*exp(j*pi/4)) < 0;
    bit1 = imag(symbol*exp(j*pi/4)) < 0;
    two_bits = [bit1 bit0];
endfunction

% init function for symbol rate processing --------------------------------------------------------

function sim_in = symbol_rate_init(sim_in)
    sim_in.Fs = Fs = 8000;

    modulation       = sim_in.modulation;
    verbose          = sim_in.verbose;
    framesize        = sim_in.framesize;
    Ntrials          = sim_in.Ntrials;
    Esvec            = sim_in.Esvec;
    phase_offset     = sim_in.phase_offset;
    w_offset         = sim_in.w_offset;
    plot_scatter     = sim_in.plot_scatter;

    Rs               = sim_in.Rs;
    Nc               = sim_in.Nc;

    hf_sim           = sim_in.hf_sim;
    nhfdelay         = sim_in.hf_delay_ms*Rs/1000;
    hf_mag_only      = sim_in.hf_mag_only;

    Nd               = sim_in.Nd;     % diveristy
    Ns               = sim_in.Ns;     % step size between pilots
    ldpc_code        = sim_in.ldpc_code;
    rate             = sim_in.ldpc_code_rate; 

    sim_in.bps = bps = 2;

    sim_in.Nsymb         = Nsymb            = framesize/bps;
    sim_in.Nsymbrow      = Nsymbrow         = Nsymb/Nc;
    sim_in.Npilotsframe  = Npilotsframe     = 2;
    sim_in.Nsymbrowpilot = Nsymbrowpilot    = Nsymbrow + Npilotsframe;
    
    if verbose == 2
      printf("Each frame contains %d data bits or %d data symbols, transmitted as %d symbols by %d carriers.", framesize, Nsymb, Nsymbrow, Nc);
      printf("  There are %d pilot symbols in each carrier together at the start of each frame, then %d data symbols.", Npilotsframe, Ns); 
      printf("  Including pilots, the frame is %d symbols long by %d carriers.\n\n", Nsymbrowpilot, Nc);
    end

    sim_in.prev_sym_tx = qpsk_mod([0 0])*ones(1,Nc*Nd);
    sim_in.prev_sym_rx = qpsk_mod([0 0])*ones(1,Nc*Nd);

    sim_in.rx_symb_buf  = zeros(3*Nsymbrow, Nc*Nd);
    sim_in.rx_pilot_buf = zeros(3*Npilotsframe,Nc*Nd);
    sim_in.tx_bits_buf  = zeros(1,2*framesize);

    % pilot sequence is used for phase and amplitude estimation, and frame sync

    pilot = 1 - 2*(rand(Npilotsframe,Nc) > 0.5);
    sim_in.pilot = pilot;
    sim_in.tx_pilot_buf = [pilot; pilot; pilot];

    if sim_in.do_write_pilot_file
      write_pilot_file(pilot, Nsymbrowpilot, Ns, Nsymbrow, Npilotsframe, Nc);
    end

    % we use first 2 pilots of next frame to help with frame sync and fine freq

    sim_in.Nct_sym_buf = 2*Nsymbrowpilot + 2;
    sim_in.ct_symb_buf = zeros(sim_in.Nct_sym_buf, Nc*Nd);

    sim_in.ff_phase = 1;

    sim_in.ct_symb_ff_buf = zeros(Nsymbrowpilot + 2, Nc*Nd);

    % Init LDPC --------------------------------------------------------------------

    if ldpc_code
        % Start CML library

        currentdir = pwd;
        addpath '~/cml/mat'    % assume the source files stored here
        cd ~/cml
        CmlStartup             % note that this is not in the cml path!
        cd(currentdir)
  
        % Our LDPC library

        ldpc;

        mod_order = 4; 
        modulation2 = 'QPSK';
        mapping = 'gray';

        sim_in.demod_type = 0;
        sim_in.decoder_type = 0;
        sim_in.max_iterations = 100;

        code_param = ldpc_init(rate, framesize, modulation2, mod_order, mapping);
        code_param.code_bits_per_frame = framesize;
        code_param.symbols_per_frame = framesize/bps;
        sim_in.code_param = code_param;
    else
        sim_in.rate = 1;
        sim_in.code_param = [];
    end
endfunction


% Symbol rate processing for tx side (modulator) -------------------------------------------------------

% legacy DQPSK mod for comparative testing

function [tx_symb prev_tx_symb] = bits_to_dqpsk_symbols(sim_in, tx_bits, prev_tx_symb)
    Nc         = sim_in.Nc;
    Nsymbrow   = sim_in.Nsymbrow;

    tx_symb = zeros(Nsymbrow,Nc);

    for c=1:Nc
      for r=1:Nsymbrow
        i = (c-1)*Nsymbrow + r;
        tx_symb(r,c) = qpsk_mod(tx_bits(2*(i-1)+1:2*i));  
        tx_symb(r,c) *= prev_tx_symb(c);
        prev_tx_symb(c) = tx_symb(r,c);
      end
    end
              
endfunction


% legacy DQPSK demod for comparative testing

function [rx_symb rx_bits rx_symb_linear prev_rx_symb] = dqpsk_symbols_to_bits(sim_in, rx_symb, prev_rx_symb)
    Nc         = sim_in.Nc;
    Nsymbrow   = sim_in.Nsymbrow;

    tx_symb = zeros(Nsymbrow,Nc);

    for c=1:Nc
      for r=1:Nsymbrow
        tmp = rx_symb(r,c);
        rx_symb(r,c) *= conj(prev_rx_symb(c))/abs(prev_rx_symb(c));
        prev_rx_symb(c) = tmp;
        i = (c-1)*Nsymbrow + r;
        rx_symb_linear(i) = rx_symb(r,c);
        rx_bits((2*(i-1)+1):(2*i)) = qpsk_demod(rx_symb(r,c));
      end
    end 
              
endfunction


function [tx_symb tx_bits] = bits_to_qpsk_symbols(sim_in, tx_bits, code_param)
    ldpc_code     = sim_in.ldpc_code;
    rate          = sim_in.ldpc_code_rate;
    framesize     = sim_in.framesize;
    Nsymbrow      = sim_in.Nsymbrow;
    Nsymbrowpilot = sim_in.Nsymbrowpilot;
    Nc            = sim_in.Nc;
    Npilotsframe  = sim_in.Npilotsframe;
    Ns            = sim_in.Ns;
    modulation    = sim_in.modulation;
    pilot         = sim_in.pilot;
    Nd            = sim_in.Nd;

    if ldpc_code
        [tx_bits, tmp] = ldpc_enc(tx_bits, code_param);
    end

    % modulate --------------------------------------------

    % organise symbols into a Nsymbrow rows by Nc cols
    % data and parity bits are on separate carriers

    tx_symb = zeros(Nsymbrow,Nc);
    
    for c=1:Nc
      for r=1:Nsymbrow
        i = (c-1)*Nsymbrow + r;
        tx_symb(r,c) = qpsk_mod(tx_bits(2*(i-1)+1:2*i));
      end
    end
    
    % insert pilots at start of frame
    
    tx_symb = [pilot(1,:); pilot(2,:); tx_symb;];

    % copy to other carriers (diversity)

    tmp = tx_symb;
    for d=1:Nd-1
      tmp = [tmp tx_symb];
    end
    tx_symb = tmp;

    % ensures energy/symbol is normalised with diversity

    tx_symb = tx_symb/sqrt(Nd);
endfunction


% Symbol rate processing for rx side (demodulator) -------------------------------------------------------

function [rx_symb rx_bits rx_symb_linear amp_ phi_ sig_rms noise_rms cohpsk] = qpsk_symbols_to_bits(cohpsk, ct_symb_buf)
    framesize     = cohpsk.framesize;
    Nsymb         = cohpsk.Nsymb;
    Nsymbrow      = cohpsk.Nsymbrow;
    Nsymbrowpilot = cohpsk.Nsymbrowpilot;
    Nc            = cohpsk.Nc;
    Nd            = cohpsk.Nd;
    Npilotsframe  = cohpsk.Npilotsframe;
    pilot         = cohpsk.pilot;
    verbose       = cohpsk.verbose;
    coh_en        = cohpsk.coh_en;

    % Use pilots to get phase and amplitude estimates We assume there
    % are two samples at the start of each frame and two at the end
    % Note: correlation (averging) method was used initially, but was
    % poor at tracking fast phase changes that we experience on fading
    % channels.  Linear regression (fitting a straight line) works
    % better on fading channels, but increases BER slightly for AWGN
    % channels.

    sampling_points = [1 2 cohpsk.Nsymbrow+3 cohpsk.Nsymbrow+4];
    pilot2 = [cohpsk.pilot(1,:); cohpsk.pilot(2,:); cohpsk.pilot(1,:); cohpsk.pilot(2,:);];
    phi_ = zeros(Nsymbrow, Nc*Nd);
    amp_ = zeros(Nsymbrow, Nc*Nd);
    
    for c=1:Nc*Nd
      y = ct_symb_buf(sampling_points,c) .* pilot2(:,c-Nc*floor((c-1)/Nc));
      [m b] = linreg(sampling_points, y, length(sampling_points));
      yfit = m*[3 4 5 6] + b;
      phi_(:, c) = angle(yfit);

      mag  = sum(abs(ct_symb_buf(sampling_points,c)));
      amp_(:, c) = mag/length(sampling_points);
    end

    % now correct phase of data symbols

    rx_symb = zeros(Nsymbrow, Nc);
    rx_symb_linear = zeros(1, Nsymbrow*Nc*Nd);
    rx_bits = zeros(1, framesize);
    for c=1:Nc*Nd
      for r=1:Nsymbrow
        if coh_en == 1
          rx_symb(r,c) = ct_symb_buf(2+r,c)*exp(-j*phi_(r,c));
        else
          rx_symb(r,c) = ct_symb_buf(2+r,c);
        end
        i = (c-1)*Nsymbrow + r;
        rx_symb_linear(i) = rx_symb(r,c);
      end
    end

    % and finally optional diversity combination and make decn on bits

    for c=1:Nc
      for r=1:Nsymbrow
        i = (c-1)*Nsymbrow + r;
        div_symb = rx_symb(r,c);
        for d=1:Nd-1
          div_symb += rx_symb(r,c + Nc*d);
        end
        rx_bits((2*(i-1)+1):(2*i)) = qpsk_demod(div_symb);
      end
    end

    % Estimate noise power from demodulated symbols.  One method is to
    % calculate the distance of each symbol from the average symbol
    % position. However this is complicated by fading, which means the
    % amplitude of the symbols is constantly changing.
    
    % Now the scatter diagram in a fading channel is a X or cross
    % shape.  The noise can be resolved into two components at right
    % angles to each other.  The component along the the "thickness"
    % of the arms is proportional to the noise power and not affected
    % by fading.  We only use points further along the real axis than
    % the mean amplitude so we keep out of the central nosiey blob
        
    sig_rms = mean(abs(rx_symb_linear));
   
    sum_x = 0;
    sum_xx = 0;
    n = 0;
    for i=1:Nsymb*Nd
      s = rx_symb_linear(i);
      if abs(real(s)) > sig_rms
        sum_x  += imag(s);
        sum_xx += imag(s)*imag(s);
        n++;
      end
    end
   
    noise_var = 0;
    if n > 1
      noise_var = (n*sum_xx - sum_x*sum_x)/(n*(n-1));
    end
    noise_rms = sqrt(noise_var);
endfunction

function [ch_symb rx_timing rx_filt rx_baseband afdmdv f_est] = rate_Fs_rx_processing(afdmdv, ch_fdm_frame, f_est, nsymb, nin, freq_track)
    M = afdmdv.M;
    
    rx_baseband = [];
    rx_filt = [];
    rx_timing = [];

    ch_fdm_frame_index = 1;

    for r=1:nsymb
      % shift signal to nominal baseband, this will put Nc/2 carriers either side of 0 Hz

      [rx_fdm_frame_bb afdmdv.fbb_phase_rx] = freq_shift(ch_fdm_frame(ch_fdm_frame_index:ch_fdm_frame_index + nin - 1), -f_est, afdmdv.Fs, afdmdv.fbb_phase_rx);
      ch_fdm_frame_index += nin;

      % downconvert each FDM carrier to Nc separate baseband signals

      [arx_baseband afdmdv]             = fdm_downconvert(afdmdv, rx_fdm_frame_bb, nin);
      [arx_filt afdmdv]                 = rx_filter(afdmdv, arx_baseband, nin);
      [rx_onesym arx_timing env afdmdv] = rx_est_timing(afdmdv, arx_filt, nin);     

      rx_baseband = [rx_baseband arx_baseband];
      rx_filt     = [rx_filt arx_filt];
      rx_timing    = [rx_timing arx_timing];
      
      ch_symb(r,:) = rx_onesym;

      % we only allow a timing shift on one symbol per frame

      if nin != M
        nin = M;
      end

      % freq tracking, see test_ftrack.m for unit test.  Placed in
      % this function as it needs to work on a symbol by symbol basis
      % rather than frame by frame.  This means the control loop
      % operates at a sample rate of Rs = 50Hz for say 1 Hz/s drift.

      if freq_track
        beta = 0.005;
        g = 0.2;

        % combine difference on phase from last symbol over Nc carriers

        mod_strip = 0;
        for c=1:afdmdv.Nc+1
          adiff = rx_onesym(c) .* conj(afdmdv.prev_rx_symb(c));
          afdmdv.prev_rx_symb(c) = rx_onesym(c);

          % 4th power strips QPSK modulation, by multiplying phase by 4
          % Using the abs value of the real coord was found to help 
          % non-linear issues when noise power was large

          amod_strip = adiff.^4;
          amod_strip = abs(real(amod_strip)) + j*imag(amod_strip);
          mod_strip += amod_strip;
        end

        % loop filter made up of 1st order IIR plus integrator.  Integerator
        % was found to be reqd 
        
        afdmdv.filt = (1-beta)*afdmdv.filt + beta*angle(mod_strip);
        f_est += g*afdmdv.filt;
      end
    end
endfunction


function ct_symb_buf = update_ct_symb_buf(ct_symb_buf, ch_symb, Nct_sym_buf, Nsymbrowpilot)

  % update memory in symbol buffer

  for r=1:Nct_sym_buf-Nsymbrowpilot
    ct_symb_buf(r,:) = ct_symb_buf(r+Nsymbrowpilot,:);
  end
  i = 1;
  for r=Nct_sym_buf-Nsymbrowpilot+1:Nct_sym_buf
    ct_symb_buf(r,:) = ch_symb(i,:);
    i++;
  end
endfunction


% returns index of start of frame and fine freq offset

function [next_sync cohpsk] = frame_sync_fine_freq_est(cohpsk, ch_symb, sync, next_sync)
  ct_symb_buf   = cohpsk.ct_symb_buf;
  Nct_sym_buf   = cohpsk.Nct_sym_buf;
  Rs            = cohpsk.Rs;
  Nsymbrowpilot = cohpsk.Nsymbrowpilot;
  Nc            = cohpsk.Nc;
  Nd            = cohpsk.Nd;

  ct_symb_buf = update_ct_symb_buf(ct_symb_buf, ch_symb, Nct_sym_buf, Nsymbrowpilot);
  cohpsk.ct_symb_buf = ct_symb_buf;
 
  % sample pilots at start of this frame and start of next frame 

  sampling_points = [1 2 cohpsk.Nsymbrow+3 cohpsk.Nsymbrow+4];
  pilot2 = [ cohpsk.pilot(1,:); cohpsk.pilot(2,:); cohpsk.pilot(1,:); cohpsk.pilot(2,:);];

  if sync == 0

    % sample correlation over 2D grid of time and fine freq points

    max_corr = 0;
    for f_fine=-20:0.25:20
      f_fine_rect = exp(-j*f_fine*2*pi*sampling_points/Rs)'; % note: this could be pre-computed at init or compile time
      for t=0:cohpsk.Nsymbrowpilot-1
        corr = 0; mag = 0;
        for c=1:Nc*Nd
          f_corr_vec = f_fine_rect .* ct_symb_buf(t+sampling_points,c); % note: this could be pre-computed at init or compile time
          acorr = 0.0;
          for p=1:length(sampling_points)
            acorr += pilot2(p,c-Nc*floor((c-1)/Nc)) * f_corr_vec(p);
            mag   += abs(f_corr_vec(p));
          end
          corr += abs(acorr);
        end

        if corr >= max_corr
          max_corr = corr;
          max_mag = mag;
          cohpsk.ct = t;
          cohpsk.f_fine_est = f_fine;
          cohpsk.ff_rect = exp(-j*f_fine*2*pi/Rs);
        end
      end
    end
    
    printf("  [%d]   fine freq f: %f max_ratio: %f ct: %d\n", cohpsk.frame, cohpsk.f_fine_est, abs(max_corr)/max_mag, cohpsk.ct);
    if abs(max_corr/max_mag) > 0.9
      printf("  [%d]   encouraging sync word! ratio: %f\n", cohpsk.frame, abs(max_corr/max_mag));
      cohpsk.sync_timer = 0;
      next_sync = 1;
    else
      next_sync = 0;
    end
    cohpsk.ratio = abs(max_corr/max_mag);
  end
  
  % single point correlation just to see if we are still in sync

  if sync == 1
    corr = 0; mag = 0;
    f_fine_rect = exp(-j*cohpsk.f_fine_est*2*pi*sampling_points/Rs)';
    for c=1:Nc*Nd
      f_corr_vec = f_fine_rect .* ct_symb_buf(cohpsk.ct+sampling_points,c);
      acorr = 0;
      for p=1:length(sampling_points)
        acorr += pilot2(p, c-Nc*floor((c-1)/Nc)) * f_corr_vec(p);
        mag  += abs(f_corr_vec(p));
      end
      corr += abs(acorr);
    end
    cohpsk.ratio = abs(corr)/mag;
  end

endfunction


% misc sync state machine code, just wanted it in a function

function [sync cohpsk] = sync_state_machine(cohpsk, sync, next_sync)

  if sync == 1

    % check that sync is still good, fall out of sync on consecutive bad frames */

    if cohpsk.ratio < 0.8
      cohpsk.sync_timer++;
    else
      cohpsk.sync_timer = 0;            
    end

    if cohpsk.sync_timer == 10
      printf("  [%d] lost sync ....\n", cohpsk.frame);
      next_sync = 0;
    end
  end

  sync = next_sync;
endfunction

