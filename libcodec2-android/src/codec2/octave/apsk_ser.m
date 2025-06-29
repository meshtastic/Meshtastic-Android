function [svec] = apsk_ser(Esvec, M)
% Return approx (uncoded) SER for 16 or 32 APSK, or plot SERs if no args
% USAGE apsk_ser();   OR   SERvec = apsk_ser(Esvec_dBs, M)
% VK5DSP,  July 2020

% Fit 3rd order polys to data in Figure 3 of
% "Perf anal of APSK mod for DVB-S2 trans over nonlinear channels"
% by Wonjin Sung et al;  
% Published in Int. J. Satellite Communications Networking, 2009
% (see Fig 1 for constellation diagrams and bit mappings)  

% from the Figure data, we get 2 polynomials ... 
%{
Fig3_Es = [10  12.5 15 17.5 20 21];
Fig3_16_SER = [2e-1 8e-2 2.5e-2 3e-3 1e-4 2e-5];
Fig3_32_SER = [4e-1 2.8e-1   1.5e-1 4e-2 1e-2  4e-3  1.05e-5];

p16 = polyfit(Fig3_Es, log10(Fig3_16_SER), 3)
p32 = polyfit([Fig3_Es 25], log10(Fig3_32_SER), 3)
%}

p16 =  [-0.001816729857582   0.053322736530042  -0.653699203208837   2.315143765468362];
p32 =  [-0.001418894437779   0.049903575028137  -0.669130206501662   2.743251206655785]; 

if nargin==0
    EStest = 10:21;
    EStest2 = 10:25;
    SER16 = polyval(p16, EStest);
    SER32 = polyval(p32, EStest2);
    figure(55);
    semilogy(EStest,  10.^SER16,  'b', EStest2,  10.^SER32, 'g')
    title('Approx Symb Error Rates for APSK')
    xlabel('Es/N0  (dB)')
    ylabel('SER');   grid on;
    legend('16APSK','32APSK');   legend('boxoff')
elseif nargin~=2,
    error('usage is apsk_ser(Esvec, M)');
    
else
    if (M~=16)&&(M~=32)
        error('M must be 16 or 32')
    end
    if min(Esvec)<8,
        error('Es/No values should be > 8 dB');
    end
    if (M==16) && (max(Esvec)>23),
        error('Es/No values should be < 23 dB');
    end
    if (M==32) && (max(Esvec)>27),
        error('Es/No values should be < 27 dB');
    end
    
    if M==16, svec = 10.^polyval(p16, Esvec);  end
    if M==32, svec = 10.^polyval(p32, Esvec);  end
    
end
