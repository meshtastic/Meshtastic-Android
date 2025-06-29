% fsk4.m
%
% Brady O'Brien October 2015
%
% 4FSK modem attempt from the DMR spec

graphics_toolkit("gnuplot");

fm; % analog FM modulator functions

pkg load signal;

% Init function for modem ------------------------------------------------------------

function fsk4_states = fsk4_init(fsk4_states,fsk4_info)
    Fs = fsk4_states.Fs = 48000;  %Sample rate
    Rs = fsk4_states.Rs = fsk4_info.rs;     %Symbol rate
    M = fsk4_states.M = fsk4_states.Fs/fsk4_states.Rs; %Samples per symbol
    
    % Set up 4FSK raised cosine filter. This probably screws up perf if we were using
    % optimal mod and dmeods but helps performance when using nasty old analog FM mods
    % and demods

    empty_filter = [zeros(1,99) 1];

    rf = (0:(Fs/2));
    %If there's no filter with this modem configuration, don't bother generating one
    if fsk4_info.no_filter
      fsk4_states.tx_filter = empty_filter;
      fsk4_states.rx_filter = empty_filter;
    else
      fsk4_states.tx_filter = fir2(400 ,rf/(Fs/2),fsk4_info.tx_filt_resp(rf));
      fsk4_states.rx_filter = fir2(400 ,rf/(Fs/2),fsk4_info.rx_filt_resp(rf));
    endif

    %fsk4_states.tx_filter = fsk4_states.rx_filter = [zeros(1,99) 1];
    %Set up the 4FSK symbols
    fsk4_states.symmap = fsk4_info.syms / fsk4_info.max_dev;
    
    fm_states.Ts = M;
    fm_states.Fs = Fs;
    fm_states.fc = 0;
    fm_states.fm_max = fsk4_info.max_dev*2;
    fm_states.fd = fsk4_info.max_dev;
    fm_states.pre_emp = fm_states.de_emp = 0;
    fm_states.output_filter = 0;
    fm_states.ph_dont_limit = 1;
    fsk4_states.fm_states = analog_fm_init(fm_states);
    fsk4_states.modinfo = fsk4_info;
    fsk4_states.verbose = 0;
endfunction 

%Integrate over data and dump every M samples
function d = idmp(data, M)
    d = zeros(1,length(data)/M);
    for i = 1:length(d)
      d(i) = sum(data(1+(i-1)*M:i*M));
    end
endfunction


% DMR modulator ----------------------------------------------------------

function [tx, tx_filt, tx_stream] = fsk4_mod(fsk4_states, tx_bits)
  verbose = fsk4_states.verbose

  hbits = tx_bits(1:2:length(tx_bits));
  lbits = tx_bits(2:2:length(tx_bits));
  %Pad odd bit lengths
  if(length(hbits)!=length(lbits))
    lbits = [lbits 0];
  end
  tx_symbols = lbits + hbits*2 + 1;
  M = fsk4_states.M;
  nsym = length(tx_symbols);
  nsam = nsym*M;

  tx_stream = zeros(1,nsam);
  for i=1:nsym
    tx_stream(1+(i-1)*M:i*M) = fsk4_states.symmap(tx_symbols(i));
  end
  tx_filt = filter(fsk4_states.tx_filter, 1, tx_stream);
  tx = analog_fm_mod(fsk4_states.fm_states, tx_filt);

  if verbose
    figure(10);
    plot(20*log10(abs(fft(tx))))
    title("Spectrum of modulated 4FSK")
  endif

endfunction


% Integrate and Dump 4FSK demod ----------------------------------------------------

function bits = fsk4_demod_thing(fsk4_states, rx)

  M = fsk4_states.M;
  Fs = fsk4_states.Fs;
  verbose = fsk4_states.verbose;
  t = (0:length(rx)-1);
  symup = fsk4_states.modinfo.syms;
  
  % Integrator is like an FIR filter with rectangular window coeffs.
  % This has some nasty side lobes so lets limit the overall amount
  % of noise getting in.  tx filter just happens to work, but I imagine
  % other LPF would as well.
 
  Fs = fsk4_states.Fs;
  rf = (0:(Fs/2));
  rx_filter_a = fir1(100 ,.2);
  rx_filter_b = fsk4_states.rx_filter;
  rx_filter_n = [zeros(1,99) 1];

  rx = filter(rx_filter_b, 1, rx);

  sym1m = exp(-j*2*pi*(symup(1)/Fs)*t).*rx;
  sym2m = exp(-j*2*pi*(symup(2)/Fs)*t).*rx;
  sym3m = exp(-j*2*pi*(symup(3)/Fs)*t).*rx;
  sym4m = exp(-j*2*pi*(symup(4)/Fs)*t).*rx;

  % this puppy found by experiment between 1 and M. Will vary with different
  % filter impulse responses, as delay will vary.  f you add M to it coarse
  % timing will adjust by 1.

  fine_timing = 54;

  sym1m = idmp(sym1m(fine_timing:length(sym1m)),M); sym1m = (real(sym1m).^2+imag(sym1m).^2);
  sym2m = idmp(sym2m(fine_timing:length(sym2m)),M); sym2m = (real(sym2m).^2+imag(sym2m).^2);
  sym3m = idmp(sym3m(fine_timing:length(sym3m)),M); sym3m = (real(sym3m).^2+imag(sym3m).^2);
  sym4m = idmp(sym4m(fine_timing:length(sym4m)),M); sym4m = (real(sym4m).^2+imag(sym4m).^2);

  
  figure(2);
  nsym = 500;
  %subplot(411); plot(sym1m(1:nsym))
  %subplot(412); plot(sym2m(1:nsym))
  %subplot(413); plot(sym3m(1:nsym))
  %subplot(414); plot(sym4m(1:nsym))
  plot((1:nsym),sym1m(1:nsym),(1:nsym),sym2m(1:nsym),(1:nsym),sym3m(1:nsym),(1:nsym),sym4m(1:nsym))
  
  [x iv] = max([sym1m; sym2m; sym3m; sym4m;]);
  bits = zeros(1,length(iv*2));
  figure(3);
  hist(iv);
  for i=1:length(iv)
    bits(1+(i-1)*2:i*2) = [[0 0];[0 1];[1 0];[1 1]](iv(i),(1:2));
  end
endfunction

function dat = bitreps(in,M)
  dat = zeros(1,length(in)*M);
  for i=1:length(in)
    dat(1+(i-1)*M:i*M) = in(i);
  end
endfunction

% Minimal Running Disparity, 4 symbol encoder
% This is a simple 1 bit to 1 symbol encoding for 4fsk modems built
% on old fashoned FM radios.
function syms = mrd4(bits)
  syms = zeros(1,length(bits));
  rd=0;
  lastsym=0;
  for n = (1:length(bits))
    bit = bits(n);
    sp = [1 3](bit+1); %Map a bit to a +1 or +3
    [x,v] = min(abs([rd+sp rd-sp])); %Select +n or -n, whichever minimizes disparity
    ssel = [sp -sp](v);
    if(ssel == lastsym)ssel = -ssel;endif %never run 2 of the same syms in a row
    syms(n) = ssel; %emit the symbol
    rd = rd + ssel; %update running disparity
    lastsym = ssel; %remember this symbol for next time
  end
endfunction

% Minimal Running Disparity, 8 symbol encoder
% This is a simple 2 bit to 1 symbol encoding for 8fsk modems built
% on old fashoned FM radios.
function syms = mrd8(bits)
  bitlen = length(bits);
  if mod(bitlen,2) == 1
    bits = [bits 0]
  endif

  syms = zeros(1,length(bits)*.5);
  rd=0;
  lastsym=0;
  for n = (1:2:length(bits))
    bit = (bits(n)*2)+bits(n+1);
    sp = [1 3 7 5](bit+1); %Map a bit to a +1 or +3
    [x,v] = min(abs([rd+sp rd-sp])); %Select +n or -n, whichever minimizes disparity
    ssel = [sp -sp](v);
    if(ssel == lastsym)ssel = -ssel;endif %never run 2 of the same syms in a row
    syms((n+1)/2) = ssel; %emit the symbol
    rd = rd + ssel; %update running disparity
    lastsym = ssel; %remember this symbol for next time
  end
endfunction

% "Manchester 4" encoding
function syms = mane4(bits)
    syms = zeros(1,floor(bits/2)*2);
    for n = (1:2:length(bits))
      bit0 = bits(n);
      bit1 = bits(n+1);
      sel = 2*bit0+bit1+1;
      syms(n:n+1) = [[3 -3];[-3 3];[1 -1];[-1 1]]( sel,(1:2) );
    end
endfunction

function out = fold_sum(in,l)
  sublen = floor(length(in)/l);
  out = zeros(1,l);
  for i=(1:sublen)
    v = in(1+(i-1)*l:i*l);
    out = out + v;
  end
endfunction

function [bits err rxphi] = fsk4_demod_fmrid(fsk4_states, rx, enable_fine_timing = 0)
  %Demodulate fsk signal with an analog fm demod
  rxd = analog_fm_demod(fsk4_states.fm_states,rx);

  M = fsk4_states.M;
  verbose = fsk4_states.verbose;
  %This is the ideal fine timing, assuming the same offset in nfbert
  fine_timing = 61;

  %This is meant to be adjusted by the fine timing estimator. comment out for
  %ideal timing
  %fine_timing = 59;
  
  %RRC filter to get rid of some of the noise
  rxd = filter(fsk4_states.rx_filter, 1, rxd);

  %Try and figure out where sampling should happen over 30 symbol periods
  diffsel = fold_sum(abs(diff( rxd(3001:3001+(M*30)) )),10);
 
  if verbose
    figure(11);
    plot(diffsel);
    title("Fine timing estimation");
  endif

  %adjust fine timing
  [v iv] = min(diffsel);
  if enable_fine_timing
    fine_timing = 59 + iv;
  endif
  rxphi = iv;

  %sample symbols
  sym = rxd(fine_timing:M:length(rxd));

  if verbose
    figure(4)
    plot(sym(1:1000));
    title("Sampled symbols")
  endif
  %eyediagram(afsym,2);
  % Demod symbol map. I should probably figure a better way to do this.
  % After sampling, the furthest symbols tend to be distributed about .80

  % A little cheating to demap the symbols
  % Take a histogram of the sampled symbols, find the center of the largest distribution,
  % and correct the symbol map to match it
  [a b] = hist(abs(sym),50);
  [a ii] = max(a);
  %grmax = abs(b(ii));
  %grmax = (grmax<.65)*.65 + (grmax>=.65)*grmax;
  grmax = .84;
  dmsyms = rot90(fsk4_states.symmap*grmax)
  (dmsyms(2)+dmsyms(1))/2

  if verbose
    figure(2)
    hist(abs(sym),200);
    title("Sampled symbol histogram")
  endif

  %demap the symbols
  [err, symout] = min(abs(sym-dmsyms));
  
  if verbose
    figure(3)
    hist(symout);
    title("De-mapped symbols")
  endif

  bits = zeros(1,length(symout)*2);
  %Translate symbols back into bits

  for i=1:length(symout)
    bits(1+(i-1)*2:i*2) = [[1 1];[1 0];[0 1];[0 0]](symout(i),(1:2));
  end
endfunction

% Frequency response of the DMR raised cosine filter 
% from ETSI TS 102 361-1 V2.2.1 page 111
dmr.tx_filt_resp = @(f) sqrt(1.0*(f<=1920) - cos((pi*f)/1920).*1.0.*(f>1920 & f<=2880));
dmr.rx_filt_resp = dmr.tx_filt_resp;
dmr.max_dev = 1944;
dmr.syms = [-1944 -648 1944 648];
dmr.rs = 4800;
dmr.no_filter = 0;
dmr.demod_fx = @fsk4_demod_fmrid;
global dmr_info = dmr;


% No-filter 4FSK 'ideal' parameters
nfl.tx_filt_resp = @(f) 1;
nfl.rx_filt_resp = nfl.tx_filt_resp;
nfl.max_dev = 7200;
%nfl.syms = [-3600 -1200 1200 3600];
nfl.syms = [-7200,-2400,2400,7200];
nfl.rs = 4800;
nfl.no_filter = 1;
nfl.demod_fx = @fsk4_demod_thing;
global nflt_info = nfl;

%Some parameters for the NXDN filters
nxdn_al = .2;
nxdn_T = 416.7e-6;
nxdn_fl = ((1-nxdn_al)/(2*nxdn_T));
nxdn_fh = ((1+nxdn_al)/(2*nxdn_T));

%Frequency response of the NXDN filters
% from NXDN TS 1-A V1.3 page 13
% Please note : NXDN not fully implemented or tested
nxdn_H = @(f) 1.0*(f<nxdn_fl) + cos( (nxdn_T/(4*nxdn_al))*(2*pi*f-(pi*(1-nxdn_al)/nxdn_T)) ).*(f<=nxdn_fh & f>nxdn_fl);
nxdn_P = @(f) (f<=nxdn_fh & f>0).*((sin(pi*f*nxdn_T))./(.00001+(pi*f*nxdn_T))) + 1.0*(f==0);
nxdn_D = @(f) (f<=nxdn_fh & f>0).*((pi*f*nxdn_T)./(.00001+sin(pi*f*nxdn_T))) + 1.0*(f==0);

nxdn.tx_filt_resp = @(f) nxdn_H(f).*nxdn_P(f);
nxdn.rx_filt_resp = @(f) nxdn_H(f).*nxdn_D(f);
nxdn.rs = 4800;
nxdn.max_dev = 1050;
nxdn.no_filter = 0;
nxdn.syms = [-1050,-350,350,1050];
nxdn.demod_fx = @fsk4_demod_fmrid;
global nxdn_info = nxdn;

% Bit error rate test ----------------------------------------------------------
% Params - aEsNodB - EbNo in decibels
%        - timing_offset - how far the fine timing is offset
%        - bitcnt - how many bits to check
%        - demod_fx - demodulator function
% Returns - ber - teh measured BER
%         - thrcoh - theory BER of a coherent demod
%         - thrncoh - theory BER of non-coherent demod
function [ber thrcoh thrncoh] = nfbert(aEsNodB,modem_config, bitcnt=100000, timing_offset = 10)

  rand('state',1); 
  randn('state',1);
  
  %How many bits should this test run?
  bitcnt = 120000;
  
  test_bits = [zeros(1,100) rand(1,bitcnt)>.5]; %Random bits. Pad with zeros to prime the filters
  fsk4_states.M = 1;
  fsk4_states = fsk4_init(fsk4_states,modem_config);
  
  %Set this to 0 to cut down on the plotting
  fsk4_states.verbose = 1; 
  Fs = fsk4_states.Fs;
  Rb = fsk4_states.Rs * 2;  % Multiply symbol rate by 2, since we have 2 bits per symbol
  
  tx = fsk4_mod(fsk4_states,test_bits);

  %add noise here
  %shamelessly copied from gmsk.m
  EsNo = 10^(aEsNodB/10);
  EbNo = EsNo
  variance = Fs/(Rb*EbNo);
  nsam = length(tx);
  noise = sqrt(variance/2)*(randn(1,nsam) + j*randn(1,nsam));
  rx    = tx*exp(j*pi/2) + noise;

  rx    = rx(timing_offset:length(rx));

  rx_bits = modem_config.demod_fx(fsk4_states,rx);
  ber = 1;
  
  %thing to account for offset from input data to output data
  %No preamble detection yet
  ox = 1;
  for offset = (1:100)
    nerr = sum(xor(rx_bits(offset:length(rx_bits)),test_bits(1:length(rx_bits)+1-offset)));
    bern = nerr/(bitcnt-offset);
    if(bern < ber)
      ox = offset;
      best_nerr = nerr;
    end
    ber = min([ber bern]);
  end
  offset = ox;
  printf("\ncoarse timing: %d nerr: %d\n", offset, best_nerr);

  % Coherent BER theory
  thrcoh = erfc(sqrt(EbNo));

  % non-coherent BER theory calculation
  % It was complicated, so I broke it up

  ms = 4;
  ns = (1:ms-1);
  as = (-1).^(ns+1);
  bs = (as./(ns+1));
  
  cs = ((ms-1)./ns);

  ds = ns.*log2(ms);
  es = ns+1;
  fs = exp( -(ds./es)*EbNo );
  
  thrncoh = ((ms/2)/(ms-1)) * sum(bs.*((ms-1)./ns).*exp( -(ds./es)*EbNo ));

endfunction

% RX fine timing estimation playground
function rxphi = fine_ex(timing_offset = 1)
  global dmr_info;
  global nxdn_info;
  global nflt_info;

  rand('state',1); 
  randn('state',1);

  bitcnt = 12051;
  test_bits = [zeros(1,100) rand(1,bitcnt)>.5]; %Random bits. Pad with zeros to prime the filters
  t_vec = [0 0 1 1];
  %test_bits = repmat(t_vec,1,ceil(24000/length(t_vec)));


  fsk4_states.M = 1;
  fsk4_states = fsk4_init(fsk4_states,dmr_info);
  Fs = fsk4_states.Fs;
  Rb = fsk4_states.Rs * 2;  %Multiply symbol rate by 2, since we have 2 bits per symbol
  
  tx = fsk4_mod(fsk4_states,test_bits);

  %add noise here
  %shamelessly copied from gmsk.m
  %EsNo = 10^(aEsNodB/10);
  %EbNo = EsNo
  %variance = Fs/(Rb*EbNo);
  %nsam = length(tx);
  %noise = sqrt(variance/2)*(randn(1,nsam) + j*randn(1,nsam));
  %rx    = tx*exp(j*pi/2) + noise;
  rx    = tx;
  rx    = rx(timing_offset:length(rx));

  [rx_bits biterr rxphi] = fsk4_demod_fmrid(fsk4_states,rx);
  ber = 1;
  
  %thing to account for offset from input data to output data
  %No preamble detection yet
  ox = 1;
  for offset = (1:100)
    nerr = sum(xor(rx_bits(offset:length(rx_bits)),test_bits(1:length(rx_bits)+1-offset)));
    bern = nerr/(bitcnt-offset);
    if(bern < ber)
      ox = offset;
      best_nerr = nerr;
    end
    ber = min([ber bern]);
  end
  offset = ox;
  printf("\ncoarse timing: %d nerr: %d\n", offset, best_nerr);

endfunction

%Run over a wide range of offsets and make sure fine timing makes sense
function fsk4_rx_phi(socket)
  %pkg load parallel
  offrange = [100:200];
  [a b c phi] = pararrayfun(1.25*nproc(),@nfbert,10*length(offrange),offrange);
  
  close all;
  figure(1);
  clf;
  plot(offrange,phi);
endfunction


% Run this function to compare the theoretical 4FSK modem performance
% with our DMR modem simulation

function fsk4_ber_curves
  global dmr_info;
  global nxdn_info;
  global nflt_info;

  EbNodB = 1:20;
  bers_tco = bers_real = bers_tnco = bers_idealsim = ones(1,length(EbNodB));

  %vectors of the same param to pass into pararrayfun
  dmr_infos = repmat(dmr_info,1,length(EbNodB));
  nflt_infos = repmat(nflt_info,1,length(EbNodB));
  thing = @fsk4_demod_thing;

  % Lovely innovation by Brady to use all cores and really speed up the simulation

  %try
    pkg load parallel
    bers_idealsim = pararrayfun(floor(1.25*nproc()),@nfbert,EbNodB,nflt_infos);
    [bers_real,bers_tco,bers_tnco] = pararrayfun(floor(1.25*nproc()),@nfbert,EbNodB,dmr_infos);
  %catch
  %  printf("You should install package parallel. It'll make this run way faster\n");
  %  for ii=(1:length(EbNodB));
      %[bers_real(ii),bers,tco(ii),bers_tnco(ii)] = nfbert(EbNodB(ii));
  %  end
  %end_try_catch

  close all
  figure(1);
  clf;
  semilogy(EbNodB, bers_tnco,'r;4FSK non-coherent theory;')
  hold on;

  semilogy(EbNodB, bers_tco,'b;4FSK coherent theory;')
  semilogy(EbNodB, bers_real ,'g;4FSK DMR simulation;')
  semilogy(EbNodB, bers_idealsim, 'v;FSK4 Ideal Non-coherent simulation;')
  hold off;
  grid("minor");
  axis([min(EbNodB) max(EbNodB) 1E-5 1])
  legend("boxoff");
  xlabel("Eb/No (dB)");
  ylabel("Bit Error Rate (BER)")

endfunction








