// load related css
const link = document.createElement('link');
link.rel = 'stylesheet';
link.href = 'control_panel/control_panel.css';
document.head.appendChild(link);

// create and attach html elements
/*
<div class="overlay" id="overlay"></div>

<div class="tab-container">
    <div class="tab" id="tab">
        ⚙
    </div>
    <div class="tab-content" id="tabContent">
        <button class="close-btn" id="closeBtn">&times;</button>
        <h2>Settings Panel</h2>
        <p>This is a sliding tab panel that demonstrates smooth animations and user interactions.</p>
        
        <details>
            <summary>Panel Features</summary>
            <div class="content">
                <p>The tab becomes visible when you hover over it, and this panel slides out smoothly when clicked.</p>
                <p>You can close this panel by clicking the X button, clicking outside the panel, or pressing the Escape key.</p>
            </div>
        </details>

        <details>
            <summary>Design Elements</summary>
            <div class="content">
                <p>The design uses modern CSS techniques including gradients, shadows, and smooth transitions to create an engaging user experience.</p>
                <p>Features include responsive design, accessibility support, and smooth animations.</p>
            </div>
        </details>

        <details>
            <summary>Technical Details</summary>
            <div class="content">
                <p>Built with vanilla HTML, CSS, and JavaScript for optimal performance.</p>
                <p>Uses CSS Grid and Flexbox for layout, and CSS transitions for animations.</p>
                <p>Keyboard navigation support included for accessibility.</p>
            </div>
        </details>
    </div>
</div>

<div class="main-content">
    <h1>Sliding Tab Demo</h1>
    <p>Hover over the tab on the left to see it appear, then click it to reveal the sliding panel. Click anywhere outside the panel to close it.</p>
</div>
*/
// Overlay
const overlay = document.createElement('div');
overlay.className = 'overlay';
overlay.id = 'overlay';
document.body.appendChild(overlay);

// Tab Container
const tabContainer = document.createElement('div');
tabContainer.className = 'tab-container';

// Tab Button
const tab = document.createElement('div');
tab.className = 'tab';
tab.id = 'tab';
tab.textContent = '⚙';
tabContainer.appendChild(tab);

// Tab Content
const tabContent = document.createElement('div');
tabContent.className = 'tab-content';
tabContent.id = 'tabContent';

// Close Button
const closeBtn = document.createElement('button');
closeBtn.className = 'close-btn';
closeBtn.id = 'closeBtn';
closeBtn.innerHTML = '&times;';
tabContent.appendChild(closeBtn);

// Heading and paragraph
const heading = document.createElement('h2');
heading.textContent = 'Settings Panel';
tabContent.appendChild(heading);

const intro = document.createElement('p');
intro.textContent = 'This is a sliding tab panel that demonstrates smooth animations and user interactions.';
tabContent.appendChild(intro);

// Helper function to create a <details> section
function createDetails(summaryText, paragraphTexts) {
    const details = document.createElement('details');
    const summary = document.createElement('summary');
    summary.textContent = summaryText;
    details.appendChild(summary);

    const contentDiv = document.createElement('div');
    contentDiv.className = 'content';

    paragraphTexts.forEach(text => {
        const p = document.createElement('p');
        p.textContent = text;
        contentDiv.appendChild(p);
    });

    details.appendChild(contentDiv);
    return details;
}

// Append details sections
tabContent.appendChild(createDetails('Panel Features', [
    'The tab becomes visible when you hover over it, and this panel slides out smoothly when clicked.',
    'You can close this panel by clicking the X button, clicking outside the panel, or pressing the Escape key.'
]));

tabContent.appendChild(createDetails('Design Elements', [
    'The design uses modern CSS techniques including gradients, shadows, and smooth transitions to create an engaging user experience.',
    'Features include responsive design, accessibility support, and smooth animations.'
]));

tabContent.appendChild(createDetails('Technical Details', [
    'Built with vanilla HTML, CSS, and JavaScript for optimal performance.',
    'Uses CSS Grid and Flexbox for layout, and CSS transitions for animations.',
    'Keyboard navigation support included for accessibility.'
]));

tabContainer.appendChild(tabContent);
document.body.appendChild(tabContainer);

// Main Content
const mainContent = document.createElement('div');
mainContent.className = 'main-content';

const mainHeading = document.createElement('h1');
mainHeading.textContent = 'Sliding Tab Demo';

const mainPara = document.createElement('p');
mainPara.textContent = 'Hover over the tab on the left to see it appear, then click it to reveal the sliding panel. Click anywhere outside the panel to close it.';

mainContent.appendChild(mainHeading);
mainContent.appendChild(mainPara);

document.body.appendChild(mainContent);


// script

let isOpen = false;

// Open panel when tab is clicked
tab.addEventListener('click', function(e) {
    e.stopPropagation();
    openPanel();
});

// Close panel when close button is clicked
closeBtn.addEventListener('click', function(e) {
    e.stopPropagation();
    closePanel();
});

// Close panel when overlay is clicked
overlay.addEventListener('click', closePanel);

// Close panel when clicking outside
document.addEventListener('click', function(e) {
    if (isOpen && !tabContent.contains(e.target) && !tab.contains(e.target)) {
        closePanel();
    }
});

// Prevent clicks inside the panel from closing it
tabContent.addEventListener('click', function(e) {
    e.stopPropagation();
});

// Close panel with Escape key
document.addEventListener('keydown', function(e) {
    if (e.key === 'Escape' && isOpen) {
        closePanel();
    }
});

function openPanel() {
    isOpen = true;
    tabContent.classList.add('open');
    overlay.classList.add('active');
    tab.classList.add('active');
}

function closePanel() {
    isOpen = false;
    tabContent.classList.remove('open');
    overlay.classList.remove('active');
    tab.classList.remove('active');
}