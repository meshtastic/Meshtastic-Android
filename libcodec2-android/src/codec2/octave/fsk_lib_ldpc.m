% fsk_lib_ldpc.m
%
% Library of common functions used for LDPC coded 4FSK modem experiments

fsk_lib;
ldpc;

% set up modem waveform, real signal that an pass through a SSB radio
function [states M] = modem_init(Rs,Fs,df)
  M  = 4;
  if Rs == 100 P=8; end
  if Rs == 400 P=10; end;
  states = fsk_init(Fs,Rs,M,P=10,nsym=100);
  states.tx_real = 1;
  states.tx_tone_separation = Rs;
  if Rs == 100
    states.ftx = 1500 -2.5*states.tx_tone_separation + states.tx_tone_separation*(1:M);
  elseif Rs == 400
    states.ftx = 600 + states.tx_tone_separation*(0:M-1);
  else
    disp("unknown symbol rate");
  end  
  states.fest_fmin = 500;
  states.fest_fmax = 2500;
  states.fest_min_spacing = Rs/2;
  states.df = df;

  states.ber_valid_thresh = 0.1;
  states.ber_invalid_thresh = 0.2;

  states.amp_scale = 1000;
end

% set up modem waveform and LPC code
function [states code_param] = fsk_lib_ldpc_init (HRA, Rs, Fs, df=0, plots=0)
  [states M] = modem_init(Rs, Fs, df);
  N = states.N;
  if plots; states.verbose = 0x4; end

  Hsize=size(HRA); 
  states.rate = (Hsize(2)-Hsize(1))/Hsize(2);
  code_param = ldpc_init_user(HRA, modulation='FSK', mod_order=states.M, mapping='gray');
  states.coden = code_param.coded_bits_per_frame;
  states.codek = code_param.data_bits_per_frame;
end
