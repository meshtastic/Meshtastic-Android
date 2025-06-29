% Test MFSK at symbol level in CML using RA LDPC codes,  Bill, June 2020
% Simulate in AWGM and plot BERs.    Assumes that CML is installed!
%
% If required setup numb of codewords (Ncw) and channel bits/sym (bps, 1 to 4)
% may also select FEC code with Ctype (1 to 3); use plt to for debug plots of LLR values 

% July 1 version allows M=8 and pads required vectors if required to makeup 3rd bit

ldpc;

%rand('seed',1);
%randn('seed',1);
format short g 
more off
init_cml();

if exist('Ncw')==0,    Ncw=100, end     %setup defaults 
if exist('plt')==0,    plt=0;   end
if exist('bps')==0,    bps=4;   end 
if exist('Ctype')==0,  Ctype=1, end

if Ctype==1
   load H_256_768_22.txt;   HRA = H_256_768_22; % rate 1/3
elseif Ctype==2
   load H_256_512_4.mat;   HRA=H;  % K=256,  rate 1/2 code
   % above code might be improved -- but still works better than rate 1/3
elseif Ctype==3
   load HRAa_1536_512.mat; % rate 3/4,  N=2k code 
else
   error('bad Ctype');
end

M=2^bps;   nos =0;    clear res
modulation = 'FSK'; mod_order=M; mapping = 'gray';
code_param = ldpc_init_user(HRA, modulation, mod_order, mapping);

[Nr Nc] = size(HRA);
Nbits = Nc - Nr;
Krate = (Nc-Nr)/Nc
framesize = Nc;
%{
[H_rows, H_cols] = Mat2Hrows(HRA);
code_param.H_rows = H_rows;
code_param.H_cols = H_cols;
code_param.P_matrix = [];
%}


S =CreateConstellation('FSK', M);
if M==2,   Ebvec=[7:0.2: 8.7],   end 
if M==4,   Ebvec=[4:0.25: 7],  end
if M==8,   Ebvec =[3.5: 0.25: 5.5];  end 
if M==16,  Ebvec=[2.5:0.25:4.8];   end

disp(['Symbol-based ' num2str(M) 'FSK sim with K=' ...
       num2str(Nbits) ', code rate=' num2str(Krate) ', #CWs=' num2str(Ncw)])



% if M=8, for 3 bits/symbol, may need to pad codeword with bits ...
if floor(Nc/bps)*bps ~= Nc
   Npad = ceil(Nc/bps) *bps-Nc
   disp('padding codeword')
else
   Npad=0; 
end 

for Eb = Ebvec
    
    Ec =  Eb + 10*log10(Krate);
    Es =  Ec + 10*log10(bps);
    Eslin = 10^(Es/10);       %Es/N0 = 1/2k_n^2

    Terrs =0;
    for nn = 1:Ncw
        
        txbits = randi(2,1,Nbits) -1;   
        
        codeword = LdpcEncode( txbits, code_param.H_rows, code_param.P_matrix );
        code_param.code_bits_per_frame = length( codeword );
        code_param.data_bits_per_frame = length(txbits);
        Nsymb = (code_param.code_bits_per_frame+Npad)/bps;      

        if Npad; codeword = [codeword   zeros(1,Npad)]; end 
        Tx = Modulate(codeword, S);
        
        kn = sqrt(1/(2*Eslin));
        Rx = Tx + kn * (randn(size(Tx)) + j*randn(size(Tx)));
        
        SNRlin = Eslin;  % Valenti calls this SNR, but seems to be Es/N0
        symL = DemodFSK(Rx, SNRlin, 2);    %demod type is nonCOH, without estimate amplitudes
        bitL = Somap(symL);

	if Npad, bitL(end-Npad+1:end)=[]; end
        if plt>0, figure(110);   hist(bitL);   title('bit LLRs')
            figure(111);   hist(bitL);   title('Sym Ls'),  pause, 
        end
        max_it =100;   decoder_type =0;
        
        [x_hat, PCcnt] = MpDecode( -bitL, code_param.H_rows, code_param.H_cols, ...
            max_it, decoder_type, 1, 1);
        Niters = sum(PCcnt~=0); 
        detected_data = x_hat(Niters,:);
        error_positions = xor( detected_data(1:code_param.data_bits_per_frame), txbits );
        
        Nerrs = sum( error_positions);
        if plt>1, figure(121);  plot(error_positions);  Nerrs,  end 
        Terrs = Terrs + Nerrs; 
    end
    
    BER = Terrs/ (Ncw*Nbits);
    
    %HDs = (sign(bitL)+1)/2;
    %NerrsHD  = sum(codeword~=HDs);
    %BER_HD = Nerrs/Nbits;
    
    nos = nos+1;
    disp('Eb    Nerrs     BER')
    res(nos, :) = [Eb, Terrs,  BER]
    
end
figure(91)
semilogy(res(:,1), res(:,3), '-x'); grid on; hold on;
%semilogy(res(:,1), berawgn(res(:,1), 'fsk', M, 'noncoherent'), 'g');
title([num2str(M) 'FSK BER with LDPC FEC'])
xlabel('Eb/N0'); ylabel('BER')






