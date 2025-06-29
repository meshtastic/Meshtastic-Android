%fsk_llr_sam  Test MFSK using David's sample-based mod/demod with CML LLR routines
%             using 2k rate 3/4 RA LDPC code.     Bill, July 2020
%
%LLR conversion options:   (Ltype)
%    1   David's original  M=2 SD to LLR routine
%    2   Bill's pdf-based  M=2 or 4 SD to LLR
%    3   Some simple HD conversions
%    4   CML approach using symbol likelihoods, then converted to bit LLRs
%
% Results from this sim are stored in "res" -- use fsk_llr_plot to see BER figs
% Use "plt" to see some useful plots (for selected Ltypes) :
%    eg 1 is SD pdf histograms; 2 is Rx PSD;  3 is bit LLR histograms
%
% Adjust Evec and Nbits as required before running. 

#{
  Example 1 2FSK:
    octave:4> fsk_cml_sam
    octave:5> fsk_llr_plot

  Example 2 4FSK:
    octave:4> M=4; fsk_cml_sam
    octave:4> fsk_llr_plot
#}

ldpc;

% define Rician pdf
% note that Valenti uses an approximation that avoids Bessel evaluation 
function y = rice(x,v,s)
  s2 = s*s; 
  y = (x / s2) .* exp(-0.5 * (x.^2 + v.^2)/s2) .* besseli(0, x*v/s2);
endfunction

function plot_pdf(v,s)
  x=(0:0.1:2*v); 
  y= rice(x, v, s); 
  figure(201);   hold on 
  plot(x,y,'g');
  %title('Rician pdf: signal carrier')
  y= rice(x, 0, s); 
  plot(x,y,'b');
  title('Rician pdf: signal and noise-only carriers')
  pause(0.01); 
endfunction 
  
% single Eb/No point simulation ---------------
function [raw_ber rx_filt rx_bits tx_symbols demapper sig_est SNRest v_est] = ...
	 	  run_single(tx_bits, M, EcNodB,  plt)
  % Ec/N0 is per channel bit
  bps = log2(M);  % bits per symbol
  Ts = 16;        % length of each symbol in samples

  if length(tx_bits)==1
    Nbits = tx_bits;
    tx_bits = randi(2,1,Nbits)-1;    % make random bits
  end

  Nbits = length(tx_bits);
  Nsymbols = Nbits/log2(M);
  tx_symbols = zeros(1,Nsymbols);

  mapper = bps:-1:1;
  % look up table demapper from symbols to bits (hard decision)
  demapper=zeros(M,bps);
  for m=1:M
    for b=1:bps
        if  bitand(m-1,b) demapper(m,bps-b+1) = 1; end
    end
  end

  % continuous phase mFSK modulator

  w(1:M) = 2*pi*(1:M)/Ts;
  tx_phase = 0;
  tx = zeros(1,Ts*Nsymbols);

  for s=1:Nsymbols
    bits_for_this_symbol = tx_bits(bps*(s-1)+1:bps*s);
    symbol_index = bits_for_this_symbol * mapper' + 1;
    tx_symbols(s) = symbol_index;
    assert(demapper(symbol_index,:) == bits_for_this_symbol);
    for k=1:Ts
        tx_phase = tx_phase + w(symbol_index);
        tx((s-1)*Ts+k) = exp(j*tx_phase);
    end
  end

  % AWGN channel noise

  EsNodB = EcNodB + 10*log10(bps);
  EsNo = 10^(EsNodB/10);
  variance = Ts/EsNo;
  noise = sqrt(variance/2)*(randn(1,Nsymbols*Ts) + j*randn(1,Nsymbols*Ts));
  rx = tx + noise;

  if plt==2,    % check the Spectrum
    [psd,Fpsd] =pwelch(rx,128,0.5,128,Ts);
    figure(110); plot(Fpsd,10*log10(psd));
    title('Rx Signal:  PSD ');
    xlabel('Freq/Rs');
    %figure(111);plot(unwrap(arg(tx)));
    pause(0.01);
  end


  % integrate and dump demodulator

  rx_bits = zeros(1,Nbits);
  rx_filt = zeros(Nsymbols,M);
  rx_pows = zeros(1,M);
  rx_nse_pow  = 0.0; rx_sig_pow =0.0;
  for s=1:Nsymbols
    arx_symb = rx((s-1)*Ts + (1:Ts));
    for m=1:M
        r= sum(exp(-j*w(m)*(1:Ts)) .* arx_symb);
        rx_pows(m)= r * conj(r);
        rx_filt(s,m) = abs(r);
    end
    [tmp symbol_index] = max(rx_filt(s,:));
    rx_sig_pow = rx_sig_pow + rx_pows(symbol_index);
    rx_pows(symbol_index)=[];
    rx_nse_pow = rx_nse_pow + sum(rx_pows)/(M-1);
    rx_bits(bps*(s-1)+1:bps*s) = demapper(symbol_index,:);
  end
  
  % using Rxpower = v^2 + sigmal^2
  rx_sig_pow = rx_sig_pow/Nsymbols;
  rx_nse_pow = rx_nse_pow/Nsymbols;
  v_est = sqrt(rx_sig_pow-rx_nse_pow); 
  SNRest = rx_sig_pow/rx_nse_pow;
  sig_est = sqrt(rx_nse_pow/2);    % for Rayleigh: 2nd raw moment = 2 .sigma^2
  Kest = rx_sig_pow/(2.0*sig_est^2) -1.0;

  Nerrors = sum(xor(tx_bits, rx_bits));
  raw_ber = Nerrors/Nbits;
  printf('Ec (dB): %4.1f  M: %2d Total bits: %5d      HD Errs: %6d (Raw) BER: %1.3f\n', ...
      EcNodB, M, Nbits, Nerrors, raw_ber);
  if plt==1, plot_hist(rx_filt,tx_symbols, M); end

endfunction 

% simulate one codeword of 2 or 4 FSK --------------------------
function [Nerrors raw_ber EcNodB] = run_single_ldpc(M, Ltype, Nbits,EbNodB, plt, HRA)

  disp([num2str(M) 'FSK coded test ...  '])
  if M==2
    bps = 1; modulation = 'FSK'; mod_order=2; mapping = 'gray';
  elseif M==4
    bps = 2; modulation = 'FSK'; mod_order=4; mapping = 'gray';
  else
    error('sorry - bad value of M!');
  end
  decoder_type = 0; max_iterations = 100;

  Hsize=size(HRA); 
  Krate = (Hsize(2)-Hsize(1))/Hsize(2); %
  EcNodB = EbNodB + 10*log10(Krate);
  code_param = ldpc_init_user(HRA, modulation, mod_order, mapping);
  Nframes = floor(Nbits/code_param.data_bits_per_frame);
  Nbits = Nframes*code_param.data_bits_per_frame; 

  % Encoder
  data_bits = round(rand(1,code_param.data_bits_per_frame));
  tx_bits = [];
  for f=1:Nframes;
    codeword_bits = LdpcEncode(data_bits, code_param.H_rows, code_param.P_matrix);
    tx_bits = [tx_bits codeword_bits];
  end

  % modem/channel simulation
  [raw_ber rx_filt rx_bits tx_symbols demapper sig_est SNRlin v_est] = ...
           run_single(tx_bits,M,EcNodB, plt );
	   
  % Decoder
  Nerrors = 0;
  for f=1:Nframes
    st = (f-1)*code_param.coded_bits_per_frame/bps + 1;
    en = st + code_param.coded_bits_per_frame/bps - 1;
    if or(Ltype==1, Ltype==3)
        if bps==1,
            sd = rx_filt(st:en,1) - rx_filt(st:en,2);
            %  OR ind = rx_filt(st:en,1) > rx_filt(st:en,2);
            %     llr = ind'*2 -1;   % this works but use SNR scaling
            if Ltype==3, HD=1; else, HD = 0;  end
            llr = sd_to_llr(sd, HD)';    % David's orig 2FSK routine 
        end
        if bps==2,
            if Ltype==3,
                llr = mfsk_hd_to_llrs(rx_filt(st:en,:), demapper);
            else
                error('Ltype =1 not provided for coded 4FSK');
            end
        end
    end
    if Ltype==2,     % SDs are converted to LLRs
        % use the SD amp estimates, try Bill's new LLR routine 
        if plt==1, plot_pdf(v_est, sig_est);  end   
        llr = mfsk_sd_to_llrs(rx_filt(st:en,:), demapper, v_est, sig_est);
    end
    if Ltype==4,
        % use CML demod:   non-coherent; still seems to need amplitude estimate  
        symL = DemodFSK(1/v_est*rx_filt(st:en,:)', SNRlin, 1);     
        llr = -Somap(symL);  % now convert to bit LLRs 
    end
    if plt==3, figure(204); hist(llr);
       title('Histogram LLR for decoder inputs'); pause(0.01); end
    
    [x_hat, PCcnt] = MpDecode(llr, code_param.H_rows, code_param.H_cols, ...
        max_iterations, decoder_type, 1, 1);
    Niters = sum(PCcnt~=0);
    detected_data = x_hat(Niters,:);
    Nerrors = Nerrors + sum(xor(data_bits, detected_data(1:code_param.data_bits_per_frame)));
  end
  
  ber = Nerrors/Nbits;
  printf('Eb (dB): %4.1f Data Bits: %4d Num CWs: %5d Tot Errs: %5d (Cod) BER: %1.3f\n', ...
        EbNodB,Nbits , Nframes, Nerrors, ber);
endfunction 

function plot_hist(rx_filt,tx_symbols, M)
  % more general version of previous fn; - plots histograms for any Tx patterns
  Smax = 36;
  X = 0:Smax-1;
  H = zeros(1,Smax);  H2 = zeros(1,Smax); s2=0.0;
  for m = 1:M
      ind = tx_symbols==m;
      ind2 =  tx_symbols~=m;
      H= H+ hist(rx_filt(ind,m),X);
      H2= H2+ hist(rx_filt(ind2,m),X);
      x=rx_filt(ind2,m);
      s2 =s2 + sum(x(:).^2)/length(x);
  end
  disp('noise RMS is ');  sqrt(s2/4)
  figure(207);  clf,  plot(X,H);
  title([num2str(M) 'FSK pdf for rx=tx symbol'])
  hold on,   plot(X,H2,'g');
  title([num2str(M) 'FSK pdf for rx!=tx symbol'])
  pause(0.1);
endfunction


% 2FSK SD -> LLR mapping that we used for Wenet SSTV system
function llr = sd_to_llr(sd, HD=0)     %    original 2FSK  + HD option  
    sd = sd / mean(abs(sd));
    x = sd - sign(sd);
    sumsq = sum(x.^2);
    summ = sum(x);
    mn = summ/length(sd);
    estvar = sumsq/length(sd) - mn*mn; 
    estEsN0 = 1/(2* estvar + 1E-3); 
    if HD==0, 
      llr = 4 * estEsN0 * sd;
    else 
       llr = 8 * estEsN0 * sign(sd);    %%%%    *4  >> *8  
    endif
endfunction



function llrs = mfsk_sd_to_llrs(SD, map, v, sig)
  % Array of MFSK SD is converted to bit LLR vector according to mapping 'map' 
  % SD is M elements wide;  map is M by log2(M); v and sig are params of Rician pdf 
  % Bill (VK5DSP) for Codec2, version 24/6/20 

  Llim = 1e-7;
  XX =1.0; %    check LLR scaling? 
  Smap = size(map); 
  Ssd = size(SD); 
  if Smap(1) == 2
    llrs = zeros(1, Ssd(1)); 
    bps = 1; 
    assert(2^bps == Ssd(2), 'wrong SD length?')
    % note that when v=0, the pdf reverts to Rayleigh 
    for kk = 1: Ssd(1)
      Prx_1 =  rice(SD(kk,2), v, sig) * rice(SD(kk,1),0,sig);
      Prx_0 =  rice(SD(kk,2), 0, sig) * rice(SD(kk,1),v,sig);

      if or(isnan(Prx_1),  Prx_1<Llim), Prx_1 = Llim; end  
      if or(isnan(Prx_0),  Prx_0<Llim), Prx_0 = Llim; end  
      llrs(kk) = -XX * log(Prx_1/Prx_0);
      %% note inversion of LLRs 
   endfor 
  else
    if map==[0 0; 0 1; 1 0; 1 1]   % a specific case for 4FSK
       llrs = zeros(2, Ssd(1));  
       for kk = 1: Ssd(1)
          % pn_m is prob of sub car n given bit m is transmitted 
          p1_0 = rice(SD(kk,1), 0, sig);  p1_1 = rice(SD(kk,1), v, sig);
          p2_0 = rice(SD(kk,2), 0, sig);  p2_1 = rice(SD(kk,2), v, sig);
          p3_0 = rice(SD(kk,3), 0, sig);  p3_1 = rice(SD(kk,3), v, sig);
          p4_0 = rice(SD(kk,4), 0, sig);  p4_1 = rice(SD(kk,4), v, sig);

          %   second bit 
          Prx_1 =  p1_0*p3_0*(p2_1*p4_0 + p2_0*p4_1);
          Prx_0 =  p2_0*p4_0*(p1_1*p3_0 + p1_0*p3_1);

          if or(isnan(Prx_1),  Prx_1<Llim), Prx_1 = Llim; end  
          if or(isnan(Prx_0),  Prx_0<Llim), Prx_0 = Llim; end  
          llrs(2,kk) = -XX*log(Prx_1/Prx_0);
       
	  %  1st  bit   (LHS) 
          Prx_1 =  p1_0*p2_0*(p3_1*p4_0 + p3_0*p4_1);
          Prx_0 =  p3_0*p4_0*(p1_1*p2_0 + p1_0*p2_1);

          if or(isnan(Prx_1),  Prx_1<Llim), Prx_1 = Llim; end  
          if or(isnan(Prx_0),  Prx_0<Llim), Prx_0 = Llim; end  
          llrs(1,kk) = -XX* log(Prx_1/Prx_0);
       endfor
       llrs= llrs(:);   % convert to single vector of LLRs
       llrs = llrs'; 
    else
      disp('the mapping is '); map  
      error('not sure how that mapping works!!')
      endif
  endif
endfunction

function llrs = mfsk_hd_to_llrs(sd, map, SNRlin=4)
% for M=4, compare these results to SD method of mfsk_sd_to_llrs
  [x, ind] = max(sd');     % find biggest SD
  b2 = map(ind', :)';      % get bit pairs 
  b1 = b2(:)';             % covert to row vector
  b1 = b1*2 -1;            % convert to -1/ +1
  llrs = -4*SNRlin*b1;     % approx scaling;  default is ~6dB SNR
endfunction


% main ------------------------------------------------------------------------------

format short
more off
init_cml();

if exist('Ctype')==0, Ctype=1, end

if Ctype==1
   load H_256_768_22.txt;   HRA = H_256_768_22; % rate 1/3
elseif Ctype==2
   load H_256_512_4.mat;   HRA=H;  % rate 1/2 code 
elseif Ctype==3
   load HRAa_1536_512.mat; % rate 3/4 2k code 
else
   error('bad Ctype');
end

Hsize=size(HRA); 
printf('using LDPC code length: %5d: Code rate:  %5.2f\n',length(HRA), (Hsize(2)-Hsize(1))/Hsize(2))   

% store results in array "res" and plot afterwards
% retain prev sims?
if exist('keep_res')==0,  nrun = 0; clear res;  end 

if exist('Nbits')==0, Nbits = 20000,   end
if exist('Evec')==0,  Evec = [5: 0.5: 9],  end
if exist('plt')==0.   plt=0;               end

if exist('M')==0,  M=2,  end

for Ltype = [  2  4]    % << add other Ltype options here, if required 
    for Eb = Evec
        if and(M==4, Ltype==1)
	  disp('skip original SD >> LLRs in 4FSK')
	else 
          [Nerr raw_ber Ec] = run_single_ldpc(M, Ltype, Nbits, Eb, plt, HRA);
          nrun = nrun+1; res(nrun,:) =  [Eb Ec M Ltype Nbits Nerr raw_ber];
	endif
    end
end
disp('results stored in array "res" - plot with fsk_cml_plot')
disp('     Eb       Ec        M     Ltype    #Bits      #Errs     Raw-BER')
for n = 1: nrun
   printf('  %5.1f    %5.1f      %3d     %3d     %6d     %6d      %5.4f \n', res(n,:))
end 


