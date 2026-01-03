%% Quality Metrics Analysis - Thesis Quality Plot
% This script loads evaluation results JSON and creates comprehensive
% figures analyzing quality metrics (BLEU, METEOR, CIDEr, SPICE) for all 6 models

clear all; close all; clc;

% Define paths
data_path = 'C:/Studia/mgr/';
output_path = 'C:/Studia/mgr/matlab/results/figures/';

% Create output directory if it doesn't exist
if ~exist(output_path, 'dir')
    mkdir(output_path);
end

% Define model information
models = struct();

models(1).name = 'OpenAI GPT-4o';
models(1).key = 'openai4_20251215_220125';
models(1).color = hex2rgb('#2E75B6'); 
models(1).category = 'Cloud';

models(2).name = 'Google Gemini';
models(2).key = 'gemini_20251212_212010';
models(2).color = hex2rgb('#4A90E2'); 
models(2).category = 'Cloud';

models(3).name = 'Azure CV';
models(3).key = 'azure_20251221_124934';
models(3).color = hex2rgb('#70B4E6'); 
models(3).category = 'Cloud';

models(4).name = 'ViT-GPT2';
models(4).key = 'vit_gpt2_20251212_215305';
models(4).color = hex2rgb('#D97634');
models(4).category = 'Local';

models(5).name = 'BLIP';
models(5).key = 'blip_20251212_213002';
models(5).color = hex2rgb('#E8913D'); 
models(5).category = 'Local';

models(6).name = 'Florence 2';
models(6).key = 'florence_merged_all';
models(6).color = hex2rgb('#F5A962'); 
models(6).category = 'Local';

% Define colors for overlaid figures
overlaid_colors = struct();
overlaid_colors(1).color = hex2rgb('#1171BE');
overlaid_colors(2).color = hex2rgb('#2FBEEF');
overlaid_colors(3).color = hex2rgb('#3BAA32');
overlaid_colors(4).color = hex2rgb('#8516D1');
overlaid_colors(5).color = hex2rgb('#EDB120');
overlaid_colors(6).color = hex2rgb('#DD5400');

% Load evaluation results JSON
json_path = [data_path, 'evaluation_results.json'];
if ~isfile(json_path)
    error(['File not found: ', json_path]);
end

json_data = jsondecode(fileread(json_path));

% Initialize storage for metrics
num_models = length(models);
model_names_cell = cell(num_models, 1);

% Metric arrays
bleu_1 = zeros(num_models, 1);
bleu_2 = zeros(num_models, 1);
bleu_3 = zeros(num_models, 1);
bleu_4 = zeros(num_models, 1);
meteor = zeros(num_models, 1);
cider = zeros(num_models, 1);
spice = zeros(num_models, 1);
num_images = zeros(num_models, 1);

% Extract metrics from JSON
for i = 1:num_models
    model_key = models(i).key;
    model_data = [];
    found = false;
    
    % Get all available keys
    alt_keys = fieldnames(json_data);
    
    % Try direct match first
    if isfield(json_data, model_key)
        model_data = json_data.(model_key);
        found = true;
    else
        % Try case-insensitive exact match
        for k = 1:length(alt_keys)
            if strcmpi(alt_keys{k}, model_key)
                model_data = json_data.(alt_keys{k});
                found = true;
                break;
            end
        end
    end
    
    % Try partial match if exact not found
    if ~found
        search_term = strrep(model_key, '_merged_all', '');
        for k = 1:length(alt_keys)
            if contains(alt_keys{k}, search_term, 'IgnoreCase', true) || ...
               contains(search_term, alt_keys{k}, 'IgnoreCase', true)
                model_data = json_data.(alt_keys{k});
                found = true;
                fprintf('Found %s using partial match: %s\n', models(i).name, alt_keys{k});
                break;
            end
        end
    end
    
    % If still not found, show what's available
    if ~found
        fprintf('Warning: Key not found for model: %s\n', models(i).name);
        fprintf('  Looking for: %s\n', model_key);
        fprintf('  Available keys in JSON:\n');
        for k = 1:length(alt_keys)
            fprintf('    - %s\n', alt_keys{k});
        end
        model_names_cell{i} = models(i).name;
        continue;
    end
    
    % Extract metrics
    if isfield(model_data, 'metrics')
        metrics = model_data.metrics;
        bleu_1(i) = metrics.Bleu_1;
        bleu_2(i) = metrics.Bleu_2;
        bleu_3(i) = metrics.Bleu_3;
        bleu_4(i) = metrics.Bleu_4;
        meteor(i) = metrics.METEOR;
        cider(i) = metrics.CIDEr;
        spice(i) = metrics.SPICE;
    end
    
    if isfield(model_data, 'num_images')
        num_images(i) = model_data.num_images;
    end
    
    model_names_cell{i} = models(i).name;
    
    fprintf('%s: BLEU_4=%.4f, CIDEr=%.4f, SPICE=%.4f, METEOR=%.4f\n', ...
        models(i).name, bleu_4(i), cider(i), spice(i), meteor(i));
end

%% Figure 2: BLEU_4 vs CIDEr vs SPICE vs METEOR - Main Metrics
figure('Name', 'Main Metrics Comparison', 'Position', [100 100 1200 600]);
set(gcf, 'Color', 'white');

ax = axes();
hold(ax, 'on');

% Define metrics and their values
metrics_list = {'BLEU_4', 'CIDEr', 'SPICE', 'METEOR'};
metrics_values = [bleu_4, cider/10, spice, meteor];
colors = [hex2rgb('#1F77B4'); hex2rgb('#FF7F0E'); hex2rgb('#2CA02C'); hex2rgb('#D62728')];

% Setup positions
num_metrics = length(metrics_list);
x_positions = 1:num_metrics;
bar_width = 0.13;
model_spacing = bar_width * (num_models + 0.5);

% Plot bars for each model
for model_idx = 1:num_models
    x_offset = x_positions + (model_idx - (num_models+1)/2) * bar_width;
    bar(ax, x_offset, metrics_values(model_idx, :), bar_width, 'FaceColor', models(model_idx).color, ...
        'EdgeColor', 'none', 'DisplayName', model_names_cell{model_idx});
end

% Styling
ax.FontSize = 11;
ax.FontName = 'Calibri';
set(ax, 'XTick', 1:num_metrics);
set(ax, 'XTickLabel', metrics_list);
ax.YLabel.String = 'Score (0-1)';
ax.YLabel.FontSize = 12;
ax.YLabel.FontName = 'Calibri';
ax.YLabel.FontWeight = 'bold';
ax.XLabel.String = 'Quality Metric';
ax.XLabel.FontSize = 12;
ax.XLabel.FontName = 'Calibri';
ax.XLabel.FontWeight = 'bold';

ax.YGrid = 'on';
ax.GridLineStyle = '--';
ax.GridAlpha = 0.3;
ax.XGrid = 'off';
ax.YLim = [0 0.5];

title('Quality Metrics Comparison (Normalized)', ...
    'FontSize', 14, 'FontName', 'Calibri', 'FontWeight', 'bold');

legend('FontSize', 10, 'FontName', 'Calibri', 'Location', 'northeast', 'Box', 'off');

set(ax, 'Box', 'on', 'LineWidth', 1.2);
ax.TickDir = 'out';
ax.TickLength = [0.00 0.00];

set(gcf, 'PaperUnits', 'inches');
set(gcf, 'PaperSize', [10 6]);
set(gcf, 'PaperPosition', [0 0 10 6]);
pause(0.1);
set(gca, 'Position', [0.12 0.16 0.83 0.78]);

print(gcf, [output_path, 'quality_main_metrics_comparison.png'], '-dpng', '-r300');
print(gcf, [output_path, 'quality_main_metrics_comparison.pdf'], '-dpdf', '-r300');
fprintf('Main metrics comparison figure saved\n');

%% Figure 2c: Overall Quality Score - Bar Chart
figure('Name', 'Overall Quality Score', 'Position', [100 100 1000 600]);
set(gcf, 'Color', 'white');

ax = axes();
hold(ax, 'on');

% Calculate overall scores first
cider_normalized_overall = cider / 10;
overall_scores = mean([bleu_4, cider_normalized_overall, spice, meteor], 2);

% Sort by overall score
[sorted_scores, sorted_idx] = sort(overall_scores, 'descend');
sorted_names = model_names_cell(sorted_idx);
sorted_colors = zeros(num_models, 3);
for i = 1:num_models
    sorted_colors(i, :) = models(sorted_idx(i)).color;
end

% Plot bars
for i = 1:num_models
    bar(ax, i, sorted_scores(i), 'FaceColor', sorted_colors(i, :), 'EdgeColor', 'none', 'LineWidth', 1.2);
end

% Styling
ax.FontSize = 11;
ax.FontName = 'Calibri';
set(ax, 'XTick', 1:num_models);
set(ax, 'XTickLabel', sorted_names);
ax.TickLabelInterpreter = 'none';
ax.YLabel.String = 'Overall Quality Score (0-1)';
ax.YLabel.FontSize = 12;
ax.YLabel.FontName = 'Calibri';
ax.YLabel.FontWeight = 'bold';

ax.YGrid = 'on';
ax.GridLineStyle = '--';
ax.GridAlpha = 0.3;
ax.XGrid = 'off';
ax.YLim = [0 0.5];

title('Overall Quality Score', ...
    'FontSize', 14, 'FontName', 'Calibri', 'FontWeight', 'bold');

% Add value labels on bars
for i = 1:num_models
    text(i, sorted_scores(i) + 0.03, sprintf('%.3f', sorted_scores(i)), ...
        'HorizontalAlignment', 'center', 'FontSize', 10, 'FontName', 'Calibri', 'FontWeight', 'bold');
end

set(ax, 'Box', 'on', 'LineWidth', 1.2);
ax.TickDir = 'out';
ax.TickLength = [0.00 0.00];

set(gcf, 'PaperUnits', 'inches');
set(gcf, 'PaperSize', [10 6]);
set(gcf, 'PaperPosition', [0 0 10 6]);
pause(0.1);
set(gca, 'Position', [0.12 0.16 0.83 0.78]);

print(gcf, [output_path, 'quality_overall_score.png'], '-dpng', '-r300');
print(gcf, [output_path, 'quality_overall_score.pdf'], '-dpdf', '-r300');
fprintf('Overall quality score figure saved\n');

%% Figure 13: Radar Chart - All Metrics Normalized (0-1)
figure('Name', 'Quality Metrics Radar', 'Position', [100 100 1000 800]);
set(gcf, 'Color', 'white');

% Define subplot positions for 2x3 grid
positions = [
    0.08 0.55 0.28 0.35;   % (1,1)
    0.40 0.55 0.28 0.35;   % (1,2)
    0.72 0.55 0.28 0.35;   % (1,3)
    0.08 0.08 0.28 0.35;   % (2,1)
    0.40 0.08 0.28 0.35;   % (2,2)
    0.72 0.08 0.28 0.35;   % (2,3)
];

% Create 2x3 radar subplots (one per model)
for model_idx = 1:num_models
    % Create polar axes with specific position
    ax = polaraxes('Position', positions(model_idx, :));
    hold(ax, 'on');
    
    % Prepare data for this model (normalized to 0-1) - 4 metrics only
    metrics = [bleu_4(model_idx); cider(model_idx)/10; spice(model_idx); meteor(model_idx)];
    
    % Ensure values are between 0 and 1
    metrics = max(0, min(1, metrics));
    
    % Add first point at end to close the polygon
    metrics_closed = [metrics; metrics(1)];
    
    % Angles for each metric (in radians)
    angles = linspace(0, 2*pi, length(metrics)+1);
    
    % Plot
    plot(ax, angles, metrics_closed, 'Color', overlaid_colors(model_idx).color, 'LineWidth', 2, 'Marker', 'o', 'MarkerSize', 6);
    fill(ax, angles, metrics_closed, overlaid_colors(model_idx).color, 'FaceAlpha', 0.25);
    
    % Set angular axis properties
    ax.ThetaTick = [0 90 180 270];
    ax.ThetaTickLabel = {'BLEU_4', 'CIDEr', 'SPICE', 'METEOR'};
    
    % Set radial axis
    ax.RLim = [0 0.5];
    ax.RGrid = 'on';
    
    ax.FontSize = 9;
    ax.FontName = 'Calibri';
    
    title(ax, model_names_cell{model_idx}, ...
        'FontSize', 11, 'FontName', 'Calibri', 'FontWeight', 'bold');
end

% Add overall title
annotation('textbox', [0.5 0.98 0.1 0.02], 'String', 'Quality Metrics Radar Chart (Normalized to 0-1)', ...
    'HorizontalAlignment', 'center', 'FontSize', 14, 'FontName', 'Calibri', 'FontWeight', 'bold', ...
    'EdgeColor', 'none');

set(gcf, 'PaperUnits', 'inches');
set(gcf, 'PaperSize', [10 8]);
set(gcf, 'PaperPosition', [0 0 10 8]);
pause(0.1);

print(gcf, [output_path, 'quality_radar_normalized.png'], '-dpng', '-r300');
print(gcf, [output_path, 'quality_radar_normalized.pdf'], '-dpdf', '-r300');
fprintf('Radar chart figure saved\n');

%% Calculate Overall Quality Score
% Normalize metrics to 0-1 scale for overall score calculation
cider_normalized_for_score = cider / 10;
overall_score = mean([bleu_4, cider_normalized_for_score, spice, meteor], 2);

%% Summary and Ranking
fprintf('\n');
fprintf('=== QUALITY METRICS SUMMARY ===\n');
fprintf('Number of images per model: %d-%d\n', min(num_images), max(num_images));
fprintf('\n');

fprintf('BLEU_4 Ranking:\n');
[~, idx] = sort(bleu_4, 'descend');
for rank = 1:num_models
    i = idx(rank);
    fprintf('  %d. %s: %.4f\n', rank, model_names_cell{i}, bleu_4(i));
end

fprintf('\nCIDEr Ranking:\n');
[~, idx] = sort(cider, 'descend');
for rank = 1:num_models
    i = idx(rank);
    fprintf('  %d. %s: %.4f\n', rank, model_names_cell{i}, cider(i));
end

fprintf('\nSPICE Ranking:\n');
[~, idx] = sort(spice, 'descend');
for rank = 1:num_models
    i = idx(rank);
    fprintf('  %d. %s: %.4f\n', rank, model_names_cell{i}, spice(i));
end

fprintf('\nMETEOR Ranking:\n');
[~, idx] = sort(meteor, 'descend');
for rank = 1:num_models
    i = idx(rank);
    fprintf('  %d. %s: %.4f\n', rank, model_names_cell{i}, meteor(i));
end

fprintf('\nOverall Quality Score Ranking:\n');
[scores_sorted, idx_sorted] = sort(overall_score, 'descend');
for rank = 1:num_models
    i = idx_sorted(rank);
    fprintf('  %d. %s: %.4f\n', rank, model_names_cell{i}, overall_score(i));
end

fprintf('\n=== FIGURE SUMMARY ===\n');
fprintf('Figures saved to: %s\n', output_path);
fprintf('Generated:\n');
fprintf('  - quality_main_metrics_comparison.png/pdf (metrics on X-axis)\n');
fprintf('  - quality_main_metrics_comparison_alt.png/pdf (models on X-axis)\n');
fprintf('  - quality_overall_score.png/pdf (overall quality ranking)\n');
fprintf('  - quality_radar_normalized.png/pdf (2x3 radar charts)\n');

%% Helper function to convert hex color to RGB
function rgb = hex2rgb(hex)
    hex = hex(2:end); % Remove '#'
    rgb = sscanf(hex, '%2x%2x%2x')' / 255;
end
