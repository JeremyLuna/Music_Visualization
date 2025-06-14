// controlPanel.js
export class ControlPanel {
    constructor(options = {}) {
        this.cssPath = options.cssPath || 'controlPanel/controlPanel.css';
        this.isOpen = false;
        this.init();
    }

    init() {
        this.loadStyles();
        this.createElements();
        this.attachEvents();
    }

    loadStyles() {
        const link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = this.cssPath;
        document.head.appendChild(link);
    }

    createElements() {
        // Overlay
        this.overlay = this.createEl('div', 'overlay', 'overlay');
        document.body.appendChild(this.overlay);

        // Tab container
        this.tabContainer = this.createEl('div', 'tab-container');
        document.body.appendChild(this.tabContainer);

        // Tab button
        this.tab = this.createEl('div', 'tab', 'tab', '⚙');
        this.tabContainer.appendChild(this.tab);

        // Tab content
        this.tabContent = this.createEl('div', 'tab-content', 'tabContent');
        this.tabContainer.appendChild(this.tabContent);

        // Close button
        this.closeBtn = this.createEl('button', 'close-btn', 'closeBtn');
        this.closeBtn.innerHTML = '&times;';
        this.tabContent.appendChild(this.closeBtn);

        // Heading and intro
        this.tabContent.appendChild(this.createTextEl('h2', 'Settings'));
        this.tabContent.appendChild(this.createTextEl('p', 'Create Audio Sources and add Signal Processors Here.'));
    }

    attachEvents() {
        this.tab.addEventListener('click', e => {
            e.stopPropagation();
            this.openPanel();
        });

        this.closeBtn.addEventListener('click', e => {
            e.stopPropagation();
            this.closePanel();
        });

        this.overlay.addEventListener('click', () => this.closePanel());

        document.addEventListener('click', e => {
            if (this.isOpen && !this.tabContent.contains(e.target) && !this.tab.contains(e.target)) {
                this.closePanel();
            }
        });

        this.tabContent.addEventListener('click', e => e.stopPropagation());

        document.addEventListener('keydown', e => {
            if (e.key === 'Escape' && this.isOpen) {
                this.closePanel();
            }
        });
    }

    createDetails(summaryText) {
        const details = document.createElement('details');
        const summary = document.createElement('summary');
        summary.textContent = summaryText;
        details.appendChild(summary);

        const contentDiv = this.createEl('div', 'content');

        details.appendChild(contentDiv);
        return [details, contentDiv];
    }

    createEl(tag, className = '', id = '', text = '') {
        const el = document.createElement(tag);
        if (className) el.className = className;
        if (id) el.id = id;
        if (text) el.textContent = text;
        return el;
    }

    createTextEl(tag, text) {
        const el = document.createElement(tag);
        el.textContent = text;
        return el;
    }

    openPanel() {
        this.isOpen = true;
        this.tabContent.classList.add('open');
        this.overlay.classList.add('active');
        this.tab.classList.add('active');
    }

    closePanel() {
        this.isOpen = false;
        this.tabContent.classList.remove('open');
        this.overlay.classList.remove('active');
        this.tab.classList.remove('active');
    }
}
