% Plot some results from FSK LLR tests 
% Assume array "res" contains rows of simulation results:::  
%   Eb  Ec  M  Ltype  Nbits  Nerr BERraw   
%   (some uncoded rows might contain -1 to indicate val is not applicable)
 
figure(102);   clf;  hold on;    

%uncoded results
sub = res(res(:,4)==-1 & res(:,3)==2, :) 
semilogy(sub(:,1), sub(:,7), 'k+--')
sub = res(res(:,4)==-1 & res(:,3)==4, :) 
semilogy(sub(:,1), sub(:,7), 'k--')

leg=[]; 
% coded results 
for M = [2 4 ] 

if M==2, lt = '-+'; else lt='-x';   end 

   sub = res(res(:,4)==1 & res(:,3)==M, :) 
   if length(sub)>0,
      semilogy(sub(:,1), sub(:,6)./sub(:,5), ['k' lt])
      leg= [leg; 'Orig LLRs'];
   end
   

sub = res(res(:,4)==2 & res(:,3)==M, :)
if length(sub)>0,
   semilogy(sub(:,1), sub(:,6)./sub(:,5), ['g' lt])
   leg= [leg; ' PDF LLRs'];
end
   
sub = res(res(:,4)==3 & res(:,3)==M, :)
if length(sub)>0
   semilogy(sub(:,1), sub(:,6)./sub(:,5), ['b' lt])
   leg= [leg; ' HD LLRs'];    
end

sub = res(res(:,4)==4 & res(:,3)==M, :)
semilogy(sub(:,1), sub(:,6)./sub(:,5), ['m' lt])
leg= [leg; ' CML LLRs']; 
endfor

ylabel('BER')
xlabel('Eb/N0 (Info Bits; dB)')
  title('MFSK LLR test (+is 2FSK, xis 4FSK')
  legend(leg)
  legend('boxoff'); 
  
if exist('plotname'), print   -dpng plotname;  disp('saved');  end    

