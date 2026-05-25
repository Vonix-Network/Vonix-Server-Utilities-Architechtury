// Prevent right-click context menu to make it feel like a native app
document.addEventListener('contextmenu', event => event.preventDefault());

const projectSelect = document.getElementById('project-select');
const platformSelect = document.getElementById('platform-select');
const taskSelect = document.getElementById('task-select');
const buildBtn = document.getElementById('build-btn');
const terminalOutput = document.getElementById('terminal-output');
const progressSection = document.querySelector('.progress-section');
const progressBar = document.getElementById('progress-bar');
const progressText = document.getElementById('progress-text');

let ws = null;

// Fetch projects on load
async function fetchProjects() {
    try {
        const response = await fetch('/api/projects');
        const projects = await response.json();
        
        projects.forEach((proj, idx) => {
            const option = document.createElement('option');
            option.value = idx.toString();
            option.textContent = proj.label;
            projectSelect.appendChild(option);
        });
    } catch (err) {
        appendLog('Failed to fetch projects. Is the server running?', 'red');
    }
}

function appendLog(message, colorClass = '') {
    const line = document.createElement('div');
    line.className = 'log-line';
    if (colorClass) {
        const classes = colorClass.split(' ');
        classes.forEach(c => line.classList.add(c));
    }
    line.textContent = message;
    terminalOutput.appendChild(line);
    terminalOutput.scrollTop = terminalOutput.scrollHeight;
}

function startBuild() {
    if (ws && ws.readyState === WebSocket.OPEN) {
        appendLog('A build is already running!', 'yellow');
        return;
    }

    terminalOutput.innerHTML = ''; // Clear terminal
    appendLog('Initializing build sequence...', 'cyan');
    
    progressSection.classList.add('active');
    progressBar.style.width = '0%';
    progressText.textContent = '0% Complete';

    const projectIdx = projectSelect.value;
    const platform = platformSelect.value;
    const task = taskSelect.value;

    const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    ws = new WebSocket(`${wsProtocol}//${window.location.host}/api/build`);

    ws.onopen = () => {
        buildBtn.disabled = true;
        buildBtn.style.opacity = '0.5';
        
        ws.send(JSON.stringify({
            project_idx: projectIdx,
            platform: platform,
            task: task
        }));
    };

    ws.onmessage = (event) => {
        const data = JSON.parse(event.data);
        
        if (data.type === 'log') {
            appendLog(data.message, data.color);
        } else if (data.type === 'progress') {
            const percent = Math.round(data.percent);
            progressBar.style.width = `${percent}%`;
            progressText.textContent = `${percent}% Complete`;
        } else if (data.type === 'status') {
            if (data.status === 'done') {
                appendLog('\nBuild sequence completed successfully.', 'green bold');
                resetBtn();
            } else if (data.status === 'error') {
                appendLog(`\nBuild sequence failed: ${data.message}`, 'red bold');
                resetBtn();
            }
        }
    };

    ws.onerror = () => {
        appendLog('WebSocket error occurred.', 'red');
        resetBtn();
    };

    ws.onclose = () => {
        resetBtn();
    };
}

function resetBtn() {
    buildBtn.disabled = false;
    buildBtn.style.opacity = '1';
    ws = null;
    
    // Optional: Hide progress bar after a few seconds
    setTimeout(() => {
        if (!ws) progressSection.classList.remove('active');
    }, 5000);
}

buildBtn.addEventListener('click', startBuild);

// Init
fetchProjects();
