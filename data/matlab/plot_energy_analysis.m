%% Energy Usage Analysis - Thesis Quality Plot
% This script loads all model benchmark CSVs and creates a professional
% figure analyzing Energy usage (mWh) for all 6 models

clear all; close all; clc;

% Define paths
data_path = 'C:/Studia/mgr/matlab/results/';
output_path = 'C:/Studia/mgr/matlab/results/figures/';

% Create output directory if it doesn't exist
if ~exist(output_path, 'dir')
    mkdir(output_path);
end

% Define model information (filename, display name, color, category)
models = struct();

models(1).file = 'openai4_20251215_220125.csv';
models(1).name = 'OpenAI GPT-4o';
models(1).color = hex2rgb('#2E75B6'); 
models(1).category = 'Cloud';

models(2).file = 'gemini_20251212_212010.csv';
models(2).name = 'Google Gemini';
models(2).color = hex2rgb('#4A90E2'); 
models(2).category = 'Cloud';

models(3).file = 'azure_20251221_124934.csv';
models(3).name = 'Azure CV';
models(3).color = hex2rgb('#70B4E6'); 
models(3).category = 'Cloud';

% Local models
models(4).file = 'vit-gpt2_20251212_215305.csv';
models(4).name = 'ViT-GPT2';
models(4).color = hex2rgb('#D97634');
models(4).category = 'Local';

models(5).file = 'blip_20251212_213002.csv';
models(5).name = 'BLIP';
models(5).color = hex2rgb('#E8913D'); 
models(5).category = 'Local';

models(6).file = 'florence_merged_all.csv';
models(6).name = 'Florence 2';
models(6).color = hex2rgb('#F5A962'); 
models(6).category = 'Local';

% Initialize storage for energy data
num_models = length(models);
energy_median = zeros(num_models, 1);
energy_mean = zeros(num_models, 1);
energy_std = zeros(num_models, 1);
energy_min = zeros(num_models, 1);
energy_max = zeros(num_models, 1);
model_names_cell = cell(num_models, 1);

% Load and process energy data
for i = 1:num_models
    filepath = [data_path, models(i).file];
    
    % Check if file exists
    if ~isfile(filepath)
        warning(['File not found: ', filepath]);
        model_names_cell{i} = models(i).name;
        continue;
    end
    
    % Read CSV file
    try
        data = readtable(filepath);
        
        % Extract energy (only successful runs with non-empty energy values)
        if ismember('energy_mwh', data.Properties.VariableNames) && ismember('success', data.Properties.VariableNames)
            % Filter successful runs and non-empty energy values
            valid_idx = (data.success == 1) & (data.energy_mwh > 0);
            energy_values = data.energy_mwh(valid_idx);
            
            if ~isempty(energy_values)
                % Calculate statistics
                energy_mean(i) = mean(energy_values);
                energy_median(i) = median(energy_values);
                energy_std(i) = std(energy_values);
                energy_min(i) = min(energy_values);
                energy_max(i) = max(energy_values);
                
                model_names_cell{i} = models(i).name;
                
                fprintf('%s: Median Energy = %.6f mWh, Mean = %.6f mWh\n', ...
                    models(i).name, energy_median(i), energy_mean(i));
            else
                warning(['No valid energy data for ', models(i).name]);
                model_names_cell{i} = models(i).name;
            end
        else
            warning(['Missing energy_mwh column in ', models(i).file]);
            model_names_cell{i} = models(i).name;
        end
        
    catch ME
        warning(['Error processing ', models(i).file, ': ', ME.message]);
        model_names_cell{i} = models(i).name;
    end
end

%% Figure: Median Energy Usage Comparison
figure('Name', 'Median Energy Usage', 'Position', [100 100 900 600]);
set(gcf, 'Color', 'white');

ax = axes();
hold(ax, 'on');

% Create bar colors
bar_colors = zeros(num_models, 3);
for i = 1:num_models
    bar_colors(i, :) = models(i).color;
end

% Create bars
b = bar(ax, energy_median, 'BarWidth', 0.6);
b.FaceColor = 'flat';
for i = 1:num_models
    b.CData(i, :) = bar_colors(i, :);
end

% Styling
ax.FontSize = 11;
ax.FontName = 'Calibri';
set(ax, 'XTick', 1:num_models);
set(ax, 'XTickLabel', model_names_cell);
ax.YLabel.String = 'Median Energy Usage (mWh)';
ax.YLabel.FontSize = 12;
ax.YLabel.FontName = 'Calibri';
ax.YLabel.FontWeight = 'bold';
ax.XLabel.String = 'Model';
ax.XLabel.FontSize = 12;
ax.XLabel.FontName = 'Calibri';
ax.XLabel.FontWeight = 'bold';

ax.YGrid = 'on';
ax.GridLineStyle = '--';
ax.GridAlpha = 0.3;
ax.XGrid = 'off';

% Add value labels on top of bars
for i = 1:num_models
    if energy_median(i) > 0
        text(i, energy_median(i) + max(energy_median)*0.02, sprintf('%.6f', energy_median(i)), ...
            'HorizontalAlignment', 'center', ...
            'FontSize', 10, ...
            'FontName', 'Calibri', ...
            'FontWeight', 'bold');
    end
end

title('Median Energy Usage Comparison', ...
    'FontSize', 14, 'FontName', 'Calibri', 'FontWeight', 'bold');

set(ax, 'Box', 'on', 'LineWidth', 1.2);
ax.TickDir = 'out';
ax.TickLength = [0.00 0.00];

set(gcf, 'PaperUnits', 'inches');
set(gcf, 'PaperSize', [9 6]);
set(gcf, 'PaperPosition', [0 0 9 6]);
pause(0.1);
set(gca, 'Position', [0.13 0.18 0.82 0.75]);

print(gcf, [output_path, 'energy_median_comparison.png'], '-dpng', '-r300');
print(gcf, [output_path, 'energy_median_comparison.pdf'], '-dpdf', '-r300');
fprintf('Median energy figure saved\n');

%% Print Summary Statistics Table
fprintf('\n');
fprintf('===== ENERGY USAGE STATISTICS SUMMARY =====\n');
fprintf('%-20s | %12s | %12s | %12s | %12s | %12s\n', ...
    'Model', 'Mean (mWh)', 'Median (mWh)', 'Std (mWh)', 'Min (mWh)', 'Max (mWh)');
fprintf('%s\n', repmat('-', 85, 1));

for i = 1:num_models
    if energy_median(i) > 0
        fprintf('%-20s | %12.6f | %12.6f | %12.6f | %12.6f | %12.6f\n', ...
            model_names_cell{i}, energy_mean(i), energy_median(i), energy_std(i), energy_min(i), energy_max(i));
    end
end

fprintf('\nFigure saved to: %s\n', output_path);
fprintf('Figure: energy_median_comparison\n');
