% fsk.m
% David Rowe Nov 2014

% Ideal non-coherent FSK and AFSK-over-analog-FM simulation. Can draw
% Eb/No curves or run single point simulations

rand('state',1); 
randn('state',1);
graphics_toolkit ("gnuplot");

fm;

function sim_out = fsk_ber_test(sim_in)
  Fs        = 96000;
  fmark     = sim_in.fmark;
  fspace    = sim_in.fspace;
  Rs        = sim_in.Rs;
  Ts        = Fs/Rs;
  emphasis  = 50E-6;
  verbose   = sim_in.verbose;

  nsym      = sim_in.nsym;
  nsam      = nsym*Ts;
  EbNodB    = sim_in.EbNodB;

  fm        = sim_in.fm;

  if fm
    fm_states.pre_emp = 0;
    fm_states.de_emp  = 0;
    fm_states.Ts      = Ts;
    fm_states.Fs      = Fs; 
    fm_states.fc      = Fs/4; 
    fm_states.fm_max  = 3E3;
    fm_states.fd      = 5E3;
    fm_states.output_filter = 1;
    fm_states = analog_fm_init(fm_states);
  end

  % simulate over a range of Eb/No values

  for ne = 1:length(EbNodB)
    Nerrs = Terrs = Tbits = 0;

    % randn() generates noise spread across the entire Fs bandwidth.
    % The power (aka variance) of this noise is N = NoFs, or No =
    % N/Fs.  The power of each bit is C=1, so the energy of each bit
    % is Eb=1/Rs.  We want to find N as a function of Eb/No, so:

    % Eb/No = (1/Rs)/(N/Fs) = Fs/(RsN)
    %     N = Fs/(Rs(Eb/No))

    aEbNodB = EbNodB(ne);
    EbNo = 10^(aEbNodB/10);
    variance = Fs/(Rs*EbNo);

    % Modulator -------------------------------

    tx_bits = round(rand(1, nsym));
    tx = zeros(1,nsam);
    tx_phase = 0;

    for i=1:nsym
      for k=1:Ts
        if tx_bits(i) == 1
          tx_phase += 2*pi*fmark/Fs;
        else
          tx_phase += 2*pi*fspace/Fs;
        end
        tx_phase = tx_phase - floor(tx_phase/(2*pi))*2*pi;
        tx((i-1)*Ts+k) = exp(j*tx_phase);
      end
    end

    % Optional AFSK over FM modulator

    if sim_in.fm
      % FM mod takes real input; +/- 1 for correct deviation
      tx = analog_fm_mod(fm_states, real(tx));
    end
  
    % Channel ---------------------------------

    % We use complex (single sided) channel simulation, as it's convenient
    % for the FM simulation.

    noise = sqrt(variance/2)*(randn(1,nsam) + j*randn(1,nsam));
    rx    = tx + noise;
    if verbose > 1
      printf("EbNo: %f Eb: %f var No: %f EbNo (meas): %f\n", 
      EbNo, var(tx)*Ts/Fs, var(noise)/Fs, (var(tx)*Ts/Fs)/(var(noise)/Fs));
    end
    save fsk tx_bits rx

    % Optional AFSK over FM demodulator

    if sim_in.fm
      % scaling factor for convenience to match pure FSK
      rx_bb = 2*analog_fm_demod(fm_states, rx);
    else
      rx_bb = rx;
    end

    % Demodulator -----------------------------

    % non-coherent FSK demod

    mark_dc  = rx_bb .* exp(-j*(0:nsam-1)*2*pi*fmark/Fs);
    space_dc = rx_bb .* exp(-j*(0:nsam-1)*2*pi*fspace/Fs);

    rx_bits = zeros(1, nsym);
    for i=1:nsym
      st = (i-1)*Ts+1;
      en = st+Ts-1;
      mark_int(i)  = sum(mark_dc(st:en));
      space_int(i) = sum(space_dc(st:en));
      rx_bits(i) = abs(mark_int(i)) > abs(space_int(i));
    end
  
    if fm
      d = fm_states.nsym_delay;
      error_positions = xor(rx_bits(1+d:nsym), tx_bits(1:(nsym-d)));
    else
      error_positions = xor(rx_bits, tx_bits);
    end
    Nerrs = sum(error_positions);
    Terrs += Nerrs;
    Tbits += length(error_positions);

    TERvec(ne) = Terrs;
    BERvec(ne) = Terrs/Tbits;

    if verbose > 1
      figure(2)
      clf
      Rx = 10*log10(abs(fft(rx)));
      plot(Rx(1:Fs/2));
      axis([1 Fs/2 0 50]);

      figure(3)
      clf;
      subplot(211)
      plot(real(rx_bb(1:Ts*20)))
      subplot(212)
      Rx_bb = 10*log10(abs(fft(rx_bb)));
      plot(Rx_bb(1:3000));
      axis([1 3000 0 50]);

      figure(4);
      subplot(211)
      stem(abs(mark_int(1:100)));
      subplot(212)
      stem(abs(space_int(1:100)));   
    end

    if verbose
      printf("EbNo (db): %3.2f Terrs: %d BER: %3.2f \n", aEbNodB, Terrs, Terrs/Tbits);
    end
  end

  sim_out.TERvec = TERvec;
  sim_out.BERvec = BERvec;
endfunction


function run_fsk_curves
  sim_in.fmark     = 1200;
  sim_in.fspace    = 2200;
  sim_in.Rs        = 1200;
  sim_in.nsym      = 12000;
  sim_in.EbNodB    = 0:2:20;
  sim_in.fm        = 0;
  sim_in.verbose   = 1;

  EbNo  = 10 .^ (sim_in.EbNodB/10);
  fsk_theory.BERvec = 0.5*exp(-EbNo/2); % non-coherent BFSK demod
  fsk_sim = fsk_ber_test(sim_in);

  sim_in.fm  = 1;
  fsk_fm_sim = fsk_ber_test(sim_in);

  % BER v Eb/No curves

  figure(1); 
  clf;
  semilogy(sim_in.EbNodB, fsk_theory.BERvec,'r;FSK theory;')
  hold on;
  semilogy(sim_in.EbNodB, fsk_sim.BERvec,'g;FSK sim;')
  semilogy(sim_in.EbNodB, fsk_fm_sim.BERvec,'b;FSK over FM sim;')
  hold off;
  grid("minor");
  axis([min(sim_in.EbNodB) max(sim_in.EbNodB) 1E-4 1])
  legend("boxoff");
  xlabel("Eb/No (dB)");
  ylabel("Bit Error Rate (BER)")

  % BER v C/No (1 Hz noise BW and Eb=C/Rs=1/Rs)
  % Eb/No = (C/Rs)/(1/(N/B))
  % C/N   = (Eb/No)*(Rs/B)

  RsOnB_dB = 10*log10(sim_in.Rs/1);
  figure(2); 
  clf;
  semilogy(sim_in.EbNodB+RsOnB_dB, fsk_theory.BERvec,'r;FSK theory;')
  hold on;
  semilogy(sim_in.EbNodB+RsOnB_dB, fsk_sim.BERvec,'g;FSK sim;')
  semilogy(sim_in.EbNodB+RsOnB_dB, fsk_fm_sim.BERvec,'b;FSK over FM sim;')
  hold off;
  grid("minor");
  axis([min(sim_in.EbNodB+RsOnB_dB) max(sim_in.EbNodB+RsOnB_dB) 1E-4 1])
  legend("boxoff");
  xlabel("C/No for Rs=1200 bit/s and 1 Hz noise bandwidth (dB)");
  ylabel("Bit Error Rate (BER)")
end

function run_fsk_single
  sim_in.fmark     = 1000;
  sim_in.fspace    = 2000;
  sim_in.Rs        = 1000;
  sim_in.nsym      = 2000;
  sim_in.EbNodB    = 7;
  sim_in.fm        = 0;
  sim_in.verbose   = 1;

  fsk_sim = fsk_ber_test(sim_in);
endfunction

# choose one of these functions below

run_fsk_curves
#run_fsk_single

