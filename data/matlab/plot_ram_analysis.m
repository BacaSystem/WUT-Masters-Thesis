%% RAM Usage Analysis - Thesis Quality Plot
% This script loads all model benchmark CSVs and creates comprehensive
% figures analyzing RAM Peak Memory usage for all 6 models

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

% Define colors for overlaid figure
overlaid_colors = struct();
overlaid_colors(1).color = hex2rgb('#1171BE');
overlaid_colors(2).color = hex2rgb('#2FBEEF');
overlaid_colors(3).color = hex2rgb('#3BAA32');
overlaid_colors(4).color = hex2rgb('#8516D1');
overlaid_colors(5).color = hex2rgb('#EDB120');
overlaid_colors(6).color = hex2rgb('#DD5400');

% Initialize storage for RAM data
num_models = length(models);
ram_data = cell(num_models, 1);
ram_mean = zeros(num_models, 1);
ram_median = zeros(num_models, 1);
ram_std = zeros(num_models, 1);
ram_min = zeros(num_models, 1);
ram_max = zeros(num_models, 1);
ram_q25 = zeros(num_models, 1);
ram_q75 = zeros(num_models, 1);
model_names_cell = cell(num_models, 1);

% Load and process RAM data
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
        
        % Extract RAM peak (only successful runs with non-empty RAM values)
        if ismember('ram_peak_mb', data.Properties.VariableNames) && ismember('success', data.Properties.VariableNames)
            % Filter successful runs and non-empty RAM values
            valid_idx = (data.success == 1) & (data.ram_peak_mb > 0);
            ram_times = data.ram_peak_mb(valid_idx);
            
            if ~isempty(ram_times)
                ram_data{i} = ram_times;
                
                % Calculate statistics
                ram_mean(i) = mean(ram_times);
                ram_median(i) = median(ram_times);
                ram_std(i) = std(ram_times);
                ram_min(i) = min(ram_times);
                ram_max(i) = max(ram_times);
                ram_q25(i) = prctile(ram_times, 25);
                ram_q75(i) = prctile(ram_times, 75);
                
                model_names_cell{i} = models(i).name;
                
                fprintf('%s: Median RAM = %.2f MB, Mean = %.2f MB\n', ...
                    models(i).name, ram_median(i), ram_mean(i));
            else
                warning(['No valid RAM data for ', models(i).name]);
                model_names_cell{i} = models(i).name;
            end
        else
            error('Missing required columns');
        end
        
    catch ME
        warning(['Error processing ', models(i).file, ': ', ME.message]);
        model_names_cell{i} = models(i).name;
    end
end

% Color the boxes
bar_colors = zeros(num_models, 3);
for i = 1:num_models
    bar_colors(i, :) = models(i).color;
end

%% Figure 2: Median RAM Usage Comparison
figure('Name', 'Median RAM Usage', 'Position', [100 100 900 600]);
set(gcf, 'Color', 'white');

ax = axes();
hold(ax, 'on');

% Create bars
b = bar(ax, ram_median, 'BarWidth', 0.6);
b.FaceColor = 'flat';
for i = 1:num_models
    b.CData(i, :) = bar_colors(i, :);
end

% Styling
ax.FontSize = 11;
ax.FontName = 'Calibri';
set(ax, 'XTick', 1:num_models);
set(ax, 'XTickLabel', model_names_cell);
ax.YLabel.String = 'Median RAM Peak Usage (MB)';
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
    if ram_median(i) > 0
        text(i, ram_median(i) + max(ram_median)*0.02, sprintf('%.2f MB', ram_median(i)), ...
            'HorizontalAlignment', 'center', ...
            'FontSize', 10, ...
            'FontName', 'Calibri', ...
            'FontWeight', 'bold');
    end
end

title('Median RAM Peak Usage for AI models', ...
    'FontSize', 14, 'FontName', 'Calibri', 'FontWeight', 'bold');

set(ax, 'Box', 'on', 'LineWidth', 1.2);
ax.TickDir = 'out';
ax.TickLength = [0.00 0.00];

set(gcf, 'PaperUnits', 'inches');
set(gcf, 'PaperSize', [9 6]);
set(gcf, 'PaperPosition', [0 0 9 6]);
pause(0.1);
set(gca, 'Position', [0.13 0.18 0.82 0.75]);

print(gcf, [output_path, 'ram_median_comparison.png'], '-dpng', '-r300');
print(gcf, [output_path, 'ram_median_comparison.pdf'], '-dpdf', '-r300');
fprintf('Median RAM figure saved\n');

%% Print Summary Statistics Table
fprintf('\n');
fprintf('===== RAM USAGE STATISTICS SUMMARY =====\n');
fprintf('%-20s | %10s | %10s | %10s | %10s | %10s\n', ...
    'Model', 'Mean (MB)', 'Median (MB)', 'Std (MB)', 'Min (MB)', 'Max (MB)');
fprintf('%s\n', repmat('-', 75, 1));

for i = 1:num_models
    if ram_median(i) > 0
        fprintf('%-20s | %10.2f | %10.2f | %10.2f | %10.2f | %10.2f\n', ...
            model_names_cell{i}, ram_mean(i), ram_median(i), ram_std(i), ram_min(i), ram_max(i));
    end
end

fprintf('\n');
fprintf('===== QUARTILE INFORMATION =====\n');
fprintf('%-20s | %10s | %10s | %10s\n', 'Model', 'Q1 (25%)', 'Median', 'Q3 (75%)');
fprintf('%s\n', repmat('-', 55, 1));

for i = 1:num_models
    if ram_median(i) > 0
        fprintf('%-20s | %10.2f | %10.2f | %10.2f\n', ...
            model_names_cell{i}, ram_q25(i), ram_median(i), ram_q75(i));
    end
end

fprintf('\nAll figures saved to: %s\n', output_path);


%FIGURE INDIVIDUAL

figure('Name', 'RAM Over Runs - Individual Models', 'Position', [100 100 1200 800]);
set(gcf, 'Color', 'white');

posOrder = [1, 3, 5, 2, 4, 6];

for model_idx = 1:num_models
    if isempty(ram_data{model_idx})
        continue;
    end
    
    subplot(3, 2, posOrder(model_idx));
    ax = gca();
    hold(ax, 'on');
    
    % Plot RAM over runs (run number on X-axis)
    run_numbers = 1:length(ram_data{model_idx});
    plot(ax, run_numbers, ram_data{model_idx}, '-', ...
        'LineWidth', 1.2, 'DisplayName', 'RAM Peak Usage');
    
    % Add horizontal line for median
    med_val = ram_median(model_idx);
    yline(ax, med_val, '--r', 'LineWidth', 1.5, 'DisplayName', sprintf('Median: %.0f MB', med_val));
    
    % Styling
    ax.FontSize = 9;
    ax.FontName = 'Calibri';
    ax.XLabel.String = 'Run Number';
    ax.XLabel.FontSize = 10;
    ax.YLabel.String = 'RAM Peak Usage [MB]';
    ax.YLabel.FontSize = 10;
    
    ax.YGrid = 'on';
    ax.GridLineStyle = '--';
    ax.GridAlpha = 0.3;
    ax.XGrid = 'off';
    
    title(sprintf('%s', model_names_cell{model_idx}), ...
        'FontSize', 11, 'FontName', 'Calibri', 'FontWeight', 'bold');
    
    legend('FontSize', 8, 'FontName', 'Calibri', 'Location', 'northwest', 'Box', 'off');
   
    set(ax, 'Box', 'on', 'LineWidth', 1);
    ax.TickDir = 'in';
    ax.TickLength = [0.00 0.00];
end

sgtitle('RAM Peak Usage', ...
    'FontSize', 14, 'FontName', 'Calibri', 'FontWeight', 'bold');

set(gcf, 'PaperUnits', 'inches');
set(gcf, 'PaperSize', [12 8]);
set(gcf, 'PaperPosition', [0 0 12 8]);
pause(0.1);

print(gcf, [output_path, 'ram_over_runs_individual_subplots.png'], '-dpng', '-r300');
print(gcf, [output_path, 'ram_over_runs_individual_subplots.pdf'], '-dpdf', '-r300');
fprintf('Individual ram plots (3x2 subplot) saved\n');















figure('Name', 'RAM - All Models', 'Position', [100 100 1000 700]);
set(gcf, 'Color', 'white');

ax = axes();
hold(ax, 'on');

% Plot all models with E2E time over runs
for i = 1:num_models
    if ~isempty(ram_data{i})
        run_numbers = 1:length(ram_data{i});
        plot(ax, run_numbers, ram_data{i}, '-', 'Color', overlaid_colors(i).color, ...
            'LineWidth', 1.2, 'DisplayName', model_names_cell{i});
    end
end

% Styling
ax.FontSize = 11;
ax.FontName = 'Calibri';
ax.XLabel.String = 'Run Number';
ax.XLabel.FontSize = 12;
ax.XLabel.FontName = 'Calibri';
ax.XLabel.FontWeight = 'bold';
ax.YLabel.String = 'RAM Peak Usage [MB]';
ax.YLabel.FontSize = 12;
ax.YLabel.FontName = 'Calibri';
ax.YLabel.FontWeight = 'bold';

ax.YGrid = 'on';
ax.GridLineStyle = '--';
ax.GridAlpha = 0.3;
ax.XGrid = 'off';

title('RAM Peak Usage - All Models', ...
    'FontSize', 14, 'FontName', 'Calibri', 'FontWeight', 'bold');

legend('FontSize', 10, 'FontName', 'Calibri', 'Location', 'best', 'Box', 'off');

set(ax, 'Box', 'on', 'LineWidth', 1.2);
ax.TickDir = 'in';
ax.TickLength = [0.00 0.00];

set(gcf, 'PaperUnits', 'inches');
set(gcf, 'PaperSize', [10 7]);
set(gcf, 'PaperPosition', [0 0 10 7]);
pause(0.1);

print(gcf, [output_path, 'ram_all_models_overlaid.png'], '-dpng', '-r300');
print(gcf, [output_path, 'ram_all_models_overlaid.pdf'], '-dpdf', '-r300');
fprintf('Combined RAM plot (all models overlaid) saved\n');

fprintf('\nFigures saved to: %s\n', output_path);
