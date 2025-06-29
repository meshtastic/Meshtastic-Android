% qpsk.m
%
% David Rowe Sep 2015
%
% Octave functions to implement a QPSK modem

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
    bit0 = real(symbol*exp(j*pi/4)) < 0;
    bit1 = imag(symbol*exp(j*pi/4)) < 0;
    two_bits = [bit1 bit0];
endfunction


% Inserts pilot symbols a frame of symbols.  The pilot symbols are
% spread evenly throughout the input frame.

function frameout = insert_pilots(framein, pilots, Npilotstep)

    lpilots = length(pilots);
    lframein = length(framein);
    frameout = zeros(1, lframein + lpilots);

    pin = 1; pout = 1; ppilots = 1;
    while (lpilots)
        %printf("pin %d pout %d ppilots %d lpilots %d\n", pin, pout, ppilots, lpilots);
        frameout(pout:pout+Npilotstep-1) = framein(pin:pin+Npilotstep-1);
        pin  += Npilotstep; 
        pout += Npilotstep;
        frameout(pout:pout) = pilots(ppilots);
        ppilots++;
        pout++;
        lpilots--;
    end
endfunction


% Removes the pilots symbols from a frame of symbols.

function frameout = remove_pilots(framein, pilots, Npilotstep)

    frameout = [];
    lpilots = length(pilots);

    pin = 1; pout = 1;
    while (lpilots)
        %printf("pin %d pout %d lpilots %d  ", pin, pout, lpilots);
        %printf("pin+spacing-1 %d lvd %d lframein: %d\n", pin+spacing-1, lvd, length(framein));
        frameout(pout:pout+Npilotstep-1) = framein(pin:pin+Npilotstep-1);
        pin  += Npilotstep+1;
        pout += Npilotstep;
        lpilots--;
    end

endfunction


% Estimate and correct phase offset using a window of Np pilots around
% current symbol

function symbpilot_rx = correct_phase_offset(aqpsk, symbpilot_rx)
  rx_pilot_buf = aqpsk.rx_pilot_buf;
  Npilotstep   = aqpsk.Npilotstep;
  Nsymb        = aqpsk.Nsymb;

  for ns=1:Npilotstep+1:Nsymb

    % update buffer of recent pilots, note we need past ones

    rx_pilot_buf(1) = rx_pilot_buf(2);
    next_pilot_index = ceil(ns/(Npilotstep+1))*(Npilotstep+1);
    rx_pilot_buf(2) = symbpilot_rx(next_pilot_index);

    % average pilot symbols to get estimate of phase

    phase_est = angle(sum(rx_pilot_buf));

    %printf("next_pilot_index: %d phase_est: %f\n", next_pilot_index, phase_est);

    % now correct the phase of each symbol

    for s=ns:ns+Npilotstep
      symbpilot_rx(s) *= exp(-j*phase_est);     
    end
  end

  aqpsk.rx_pilot_buf = rx_pilot_buf;
endfunction


% builds up a sparse QPSK modulated version version of the UW for use
% in UW sync at the rx

function mod_uw = build_mod_uw(uw, spacing)
    luw = length(uw);

    mod_uw = [];

    pout = 1; puw = 1;
    while (luw)
        %printf("pin %d pout %d puw %d luw %d\n", pin, pout, puw, luw);
        pout += spacing/2;
        mod_uw(pout) = qpsk_mod(uw(puw:puw+1));
        puw += 2;
        pout += 1;
        luw -= 2;
    end
endfunction


% Uses the UW to determine when we have a full codeword ready for decoding

function [found_uw corr] = look_for_uw(mem_rx_symbols, mod_uw)
    sparse_mem_rx_symbols = mem_rx_symbols(find(mod_uw));

    % correlate with ref UW

    num = (mem_rx_symbols * mod_uw') .^ 2;
    den = (sparse_mem_rx_symbols * sparse_mem_rx_symbols') * (mod_uw * mod_uw');
    
    corr = abs(num/(den+1E-6));
    found_uw = corr > 0.8;
endfunction

