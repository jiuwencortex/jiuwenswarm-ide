import * as vscode from 'vscode';
import { WsClient, WsStatus } from '../client/WsClient';

export class StatusBar {
  private readonly item: vscode.StatusBarItem;

  constructor(private readonly ws: WsClient) {
    this.item = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 100);
    this.item.command = 'jiuwenswarm.openChat';
    this.ws.on('status', (s) => this.update(s));
    this.update(ws.getStatus());
    this.item.show();
  }

  private update(s: WsStatus): void {
    switch (s) {
      case 'connected':
        this.item.text = '$(check) JiuwenSwarm';
        this.item.tooltip = 'JiuwenSwarm: Connected — click to open chat';
        this.item.backgroundColor = undefined;
        break;
      case 'connecting':
        this.item.text = '$(loading~spin) JiuwenSwarm';
        this.item.tooltip = 'JiuwenSwarm: Connecting…';
        this.item.backgroundColor = undefined;
        break;
      case 'reconnecting':
        this.item.text = '$(sync~spin) JiuwenSwarm';
        this.item.tooltip = 'JiuwenSwarm: Reconnecting…';
        this.item.backgroundColor = new vscode.ThemeColor('statusBarItem.warningBackground');
        break;
      case 'disconnected':
        this.item.text = '$(circle-slash) JiuwenSwarm';
        this.item.tooltip = 'JiuwenSwarm: Disconnected — click to reconnect';
        this.item.backgroundColor = new vscode.ThemeColor('statusBarItem.errorBackground');
        break;
    }
  }

  dispose(): void {
    this.item.dispose();
  }
}
