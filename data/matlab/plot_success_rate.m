%% Success Rate Comparison - Thesis Quality Plot
% This script loads all model benchmark CSVs and creates a professional
% comparison figure showing success rates for all 6 models

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

% Local models (Purple shades)
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




% Total expected runs
total_runs = 500;

% Calculate success rates
num_models = length(models);
success_rates = zeros(num_models, 1);
successful_runs_count = zeros(num_models, 1);
model_names_cell = cell(num_models, 1);

for i = 1:num_models
    filepath = [data_path, models(i).file];
    
    % Check if file exists
    if ~isfile(filepath)
        warning(['File not found: ', filepath]);
        success_rates(i) = NaN;
        successful_runs_count(i) = 0;
        model_names_cell{i} = models(i).name;
        continue;
    end
    
    % Read CSV file
    try
        data = readtable(filepath);
        
        % Count successful runs (success column == 1)
        if ismember('success', data.Properties.VariableNames)
            successful_runs = sum(data.success == 1);
            successful_runs_count(i) = successful_runs;
        else
            error('No "success" column found in file');
        end
        
        % Calculate success rate as percentage
        success_rates(i) = (successful_runs / total_runs) * 100;
        model_names_cell{i} = models(i).name;
        
        fprintf('%s: %d/%d runs successful (%.2f%%)\n', ...
            models(i).name, successful_runs, total_runs, success_rates(i));
        
    catch ME
        warning(['Error processing ', models(i).file, ': ', ME.message]);
        success_rates(i) = NaN;
        successful_runs_count(i) = 0;
        model_names_cell{i} = models(i).name;
    end
end

%% Create Figure - Success Rate Comparison
figure('Name', 'Success Rate Comparison', 'Position', [100 100 900 600]);
set(gcf, 'Color', 'white');

% Create bar plot
ax = axes();
hold(ax, 'on');

% Extract colors for each model
bar_colors = zeros(num_models, 3);
for i = 1:num_models
    bar_colors(i, :) = models(i).color;
end

% Create bars
b = bar(ax, success_rates, 'BarWidth', 0.6);

% Set colors
b.FaceColor = 'flat';
for i = 1:num_models
    b.CData(i, :) = bar_colors(i, :);
end

% Styling
ax.FontSize = 11;
ax.FontName = 'Calibri';
set(ax, 'XTick', 1:num_models);
set(ax, 'XTickLabel', model_names_cell);
%xtickangle(45);
ax.YLabel.String = 'Success Rate (%)';
ax.YLabel.FontSize = 12;
ax.YLabel.FontName = 'Calibri';
ax.YLabel.FontWeight = 'bold';
ax.XLabel.String = 'Model';
ax.XLabel.FontSize = 12;
ax.XLabel.FontName = 'Calibri';
ax.XLabel.FontWeight = 'bold';

% Set Y-axis limits
ylim([96 100.5]);
ax.YTick = 96:1:100;
ax.YGrid = 'on';
ax.GridLineStyle = '--';
ax.GridAlpha = 0.3;
ax.XGrid = 'off';

% Add percentage labels on top of bars
for i = 1:num_models
    if ~isnan(success_rates(i))
        % Percentage label
        text(i, success_rates(i) + 0.15, sprintf('%.1f%%', success_rates(i)), ...
            'HorizontalAlignment', 'center', ...
            'FontSize', 10, ...
            'FontName', 'Calibri', ...
            'FontWeight', 'bold');
        
        % Run count label below percentage
        text(i, success_rates(i) - 0.15, sprintf('%d/%d', successful_runs_count(i), total_runs), ...
            'HorizontalAlignment', 'center', ...
            'FontSize', 9, ...
            'Color', [1, 1, 1], ...
            'FontName', 'Calibri');
    end
end

% Add title
title('Success Rate Comparison', ...
    'FontSize', 14, 'FontName', 'Calibri', 'FontWeight', 'bold');

% Adjust layout
set(ax, 'Box', 'on', 'LineWidth', 1.2);
ax.TickDir = 'out';
ax.TickLength = [0.00 0.00];

% Adjust figure layout to prevent label cutoff
set(gcf, 'PaperUnits', 'inches');
set(gcf, 'PaperSize', [9 6]);
set(gcf, 'PaperPosition', [0 0 9 6]);
pause(0.1); % Allow time for layout adjustment
set(gca, 'Position', [0.13 0.18 0.82 0.75]);

% Save figure
print(gcf, [output_path, 'success_rate_comparison.png'], '-dpng', '-r300');
print(gcf, [output_path, 'success_rate_comparison.pdf'], '-dpdf', '-r300');

fprintf('\nFigure saved to: %s\n', output_path);
fprintf('Files: success_rate_comparison.png and .pdf\n');

% Display summary statistics
fprintf('\n=== Summary Statistics ===\n');
fprintf('Mean Success Rate: %.2f%%\n', nanmean(success_rates));
fprintf('Median Success Rate: %.2f%%\n', nanmedian(success_rates));
fprintf('Min Success Rate: %.2f%%\n', nanmin(success_rates));
fprintf('Max Success Rate: %.2f%%\n', nanmax(success_rates));
fprintf('Standard Deviation: %.2f%%\n', nanstd(success_rates));
