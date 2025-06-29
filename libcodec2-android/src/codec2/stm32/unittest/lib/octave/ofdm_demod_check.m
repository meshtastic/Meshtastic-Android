% ofdm_demod_check.m
%
% Load results from reference and stm32 run and compare

addpath("../../lib/octave")

% Constants (would prefer paramters)
err_limit = 0.001;

% Reference
load("ofdm_demod_ref_log.txt");

% DUT
load("ofdm_demod_log.txt");

% Eliminate trailing rows of all zeros (unused)
sums_ref = sum(payload_syms_log_c, 2);
last_ref = find(sums_ref, 1, 'last');
sums_dut = sum(payload_syms_log_stm32, 2);
last_dut = find(sums_dut, 1, 'last');
last_all = max(last_ref, last_dut);

syms_ref = payload_syms_log_c(1:last_all,:);
syms_dut = payload_syms_log_stm32(1:last_all,:);

% error values
err = abs(syms_ref - syms_dut);
err_max = max(max(err));
printf("MAX_ERR %f\n", err_max);

err_vals = err - err_limit;
err_vals(err_vals<0) = 0;
errors = err_vals > 0;
num_errors = nnz(errors);

%% TODO, print errors info (count locations,...)
if (num_errors > 0)
    printf("%d ERRORS\n", num_errors);
else
    printf("PASSED\n");
end

% EVM 
evm = sqrt(meansq(err, 2));
evm_avg = mean(evm);
printf("AVG_EVM %f\n", evm_avg);
evm_max = max(evm);
printf("MAX_EVM %f\n", evm_max);

% Standard deviation
sdv = std(err, 0, 2);
sdv_max = max(sdv);
printf("MAX_SDV %f\n", sdv_max);

% Plot
figure(1)
figure(1, "visible", true)
scatter(real(syms_ref), imag(syms_ref), "g", "+")
hold on
scatter(real(syms_dut), imag(syms_dut), "b", "o")
print(1, "syms_plot.png", "-dpng")
hold off
